package com.dazo66.service;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stores immutable, content-addressed copies of artist icons and banners.
 */
@Component
public class ArtistImageCache {

    private static final long MAX_IMAGE_BYTES = 25L * 1024L * 1024L;
    private static final Pattern CACHE_FILE_NAME = Pattern.compile(
            "^artist-\\d+-(?:icon|banner)-[0-9a-f]{32}\\.(?:jpg|png|gif|webp|bmp|avif|heic|heif)$");
    private static final String PUBLIC_PATH = "/api/fanbox/artist-image/";

    private final Path cacheRoot;

    @Autowired
    public ArtistImageCache(@Value("${artist.image.cache.path:cache/artist-images}") String cachePath) {
        this(resolveCachePath(cachePath));
    }

    public ArtistImageCache(Path cacheRoot) {
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
    }

    public String download(OkHttpClient client, Request request, int artistId, String kind) throws IOException {
        if (!("icon".equals(kind) || "banner".equals(kind))) {
            throw new IllegalArgumentException("Unsupported artist image kind: " + kind);
        }
        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();
        try {
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Artist image returned HTTP " + response.code());
            }
            String extension = imageExtension(body.contentType());
            if (body.contentLength() > MAX_IMAGE_BYTES) {
                throw new IOException("Artist image exceeds cache size limit");
            }
            Files.createDirectories(cacheRoot);
            Path temporary = Files.createTempFile(cacheRoot, "artist-image-", ".tmp");
            try {
                MessageDigest digest = sha256();
                long copied = copy(body.byteStream(), temporary, digest);
                if (copied == 0) {
                    throw new IOException("Artist image response was empty");
                }
                if (extension == null) {
                    extension = detectImageExtension(temporary);
                }
                if (extension == null) {
                    throw new IOException("Artist image has unsupported content type: " + body.contentType());
                }
                String fileName = "artist-" + artistId + "-" + kind + "-"
                        + firstHexBytes(digest.digest(), 16) + "." + extension;
                Path target = cacheRoot.resolve(fileName).normalize();
                if (!cacheRoot.equals(target.getParent())) {
                    throw new IOException("Artist image cache path is invalid");
                }
                if (Files.isRegularFile(target) && Files.size(target) > 0) {
                    Files.deleteIfExists(temporary);
                } else {
                    moveAtomically(temporary, target);
                }
                return fileName;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } finally {
            if (body != null) {
                body.close();
            }
        }
    }

    public Path resolve(String fileName) {
        if (!isCacheFileName(fileName)) {
            return null;
        }
        Path file = cacheRoot.resolve(fileName).normalize();
        return cacheRoot.equals(file.getParent()) && Files.isRegularFile(file) ? file : null;
    }

    public void deleteIfObsolete(String oldFileName, String currentFileName) {
        if (!isCacheFileName(oldFileName) || oldFileName.equals(currentFileName)) {
            return;
        }
        Path oldFile = cacheRoot.resolve(oldFileName).normalize();
        if (!cacheRoot.equals(oldFile.getParent())) {
            return;
        }
        try {
            Files.deleteIfExists(oldFile);
        } catch (IOException ignored) {
            // An obsolete file can be retried on a later refresh without affecting serving.
        }
    }

    public static String publicUrl(String fileName) {
        return isCacheFileName(fileName) ? PUBLIC_PATH + fileName : null;
    }

    public static boolean isCacheFileName(String fileName) {
        return fileName != null && CACHE_FILE_NAME.matcher(fileName).matches();
    }

    private long copy(InputStream input, Path target, MessageDigest digest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream source = input; OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("Artist image exceeds cache size limit");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static Path resolveCachePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        return path.isAbsolute() ? path : Paths.get("").toAbsolutePath().resolve(path);
    }

    private static String imageExtension(MediaType contentType) {
        if (contentType == null || !"image".equalsIgnoreCase(contentType.type())) {
            return null;
        }
        String subtype = contentType.subtype().toLowerCase(Locale.ROOT);
        if ("jpeg".equals(subtype) || "jpg".equals(subtype) || "pjpeg".equals(subtype)) {
            return "jpg";
        }
        if ("png".equals(subtype) || "apng".equals(subtype)) {
            return "png";
        }
        if ("gif".equals(subtype) || "webp".equals(subtype) || "bmp".equals(subtype)
                || "avif".equals(subtype) || "heic".equals(subtype) || "heif".equals(subtype)) {
            return subtype;
        }
        return null;
    }

    private static String detectImageExtension(Path file) throws IOException {
        byte[] header = new byte[64];
        int length;
        try (InputStream input = Files.newInputStream(file)) {
            length = input.read(header);
        }
        if (length >= 3 && unsigned(header[0]) == 0xff && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return "jpg";
        }
        if (length >= 8 && unsigned(header[0]) == 0x89 && asciiAt(header, 1, "PNG\r\n\u001a\n", length)) {
            return "png";
        }
        if (asciiAt(header, 0, "GIF87a", length) || asciiAt(header, 0, "GIF89a", length)) {
            return "gif";
        }
        if (length >= 12 && asciiAt(header, 0, "RIFF", length) && asciiAt(header, 8, "WEBP", length)) {
            return "webp";
        }
        if (length >= 2 && asciiAt(header, 0, "BM", length)) {
            return "bmp";
        }
        if (length >= 12 && asciiAt(header, 4, "ftyp", length)) {
            for (int offset = 8; offset + 4 <= length; offset += 4) {
                String brand = new String(header, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if ("avif".equals(brand) || "avis".equals(brand)) {
                    return "avif";
                }
                if ("heic".equals(brand) || "heix".equals(brand) || "hevc".equals(brand)
                        || "hevx".equals(brand) || "heif".equals(brand)) {
                    return "heic";
                }
            }
        }
        return null;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String expected, int length) {
        if (offset < 0 || offset + expected.length() > length) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (unsigned(bytes[offset + index]) != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String firstHexBytes(byte[] bytes, int count) {
        StringBuilder result = new StringBuilder(count * 2);
        for (int index = 0; index < Math.min(count, bytes.length); index++) {
            result.append(String.format("%02x", bytes[index] & 0xff));
        }
        return result.toString();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}

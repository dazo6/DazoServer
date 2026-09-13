package com.dazo66.controller;

import com.dazo66.dashboard.ArtistGalleryData;
import com.dazo66.dashboard.ArtistGalleryItem;
import com.dazo66.dashboard.ArtistManagementItem;
import com.dazo66.entity.Artist;
import com.dazo66.service.ArtistGalleryCache;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.ResultEntity;
import com.dazo66.util.StringUtil;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves downloaded images and videos for an individual artist's gallery.
 */
@RestController
@RequestMapping("api/fanbox")
public class ArtistGalleryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtistGalleryController.class);
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif", "jfif"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "webm", "mov", "m4v", "ogv", "ogg", "mkv", "avi"
    ));
    private static final Object[] THUMBNAIL_LOCKS = thumbnailLocks();
    private static final int THUMBNAIL_MAX_WIDTH = 600;
    private static final int THUMBNAIL_MAX_HEIGHT = 1200;
    private static final Pattern LEADING_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    private final KemonoArtistService artistService;
    private final ArtistManagementService artistManagementService;
    private final ArtistGalleryCache galleryCache;
    private final Path downloadRoot;
    private final Path thumbnailCacheRoot;

    @Autowired
    public ArtistGalleryController(KemonoArtistService artistService,
                                   ArtistManagementService artistManagementService,
                                   @Value("${fanbox.save.path}") String downloadPath,
                                   ArtistGalleryCache galleryCache) {
        this(artistService, artistManagementService, downloadPath,
                Paths.get("").toAbsolutePath().normalize().resolve("cache").resolve("gallery-thumbnails"),
                galleryCache);
    }

    ArtistGalleryController(KemonoArtistService artistService,
                            ArtistManagementService artistManagementService,
                            String downloadPath,
                            Path thumbnailCacheRoot) {
        this(artistService, artistManagementService, downloadPath, thumbnailCacheRoot,
                new ArtistGalleryCache(Paths.get(downloadPath),
                        thumbnailCacheRoot.resolveSibling("gallery-indexes"), new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    ArtistGalleryController(KemonoArtistService artistService,
                            ArtistManagementService artistManagementService,
                            String downloadPath,
                            Path thumbnailCacheRoot,
                            ArtistGalleryCache galleryCache) {
        this.artistService = artistService;
        this.artistManagementService = artistManagementService;
        this.galleryCache = galleryCache;
        this.downloadRoot = Paths.get(downloadPath).toAbsolutePath().normalize();
        this.thumbnailCacheRoot = thumbnailCacheRoot.toAbsolutePath().normalize();
    }

    @GetMapping("{id}/gallery")
    public ResponseEntity<ResultEntity<ArtistGalleryData>> getGallery(
            @PathVariable int id,
            @RequestParam(required = false, defaultValue = "album") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        Artist artist = artistService.getById(id);
        if (artist == null) {
            return ResponseEntity.notFound().build();
        }
        String artistDirectoryName = artistDirectoryName(artist);
        ArtistManagementItem artistItem = artistManagementService.toItem(artist);
        artistItem.setArtistDirectory(artistDirectoryName);
        try {
            List<ArtistGalleryCache.CachedImage> cachedImages = galleryCache.getOrBuild(artist);
            List<ArtistGalleryItem> items = new ArrayList<>(cachedImages.size());
            String encodedDirectory = encode(artistDirectoryName);
            for (ArtistGalleryCache.CachedImage cachedImage : cachedImages) {
                items.add(toGalleryItem(cachedImage, encodedDirectory));
            }
            items.sort(galleryComparator(sortBy, order));
            return galleryResponse(artistItem, items);
        } catch (IOException exception) {
            return ResponseEntity.status(500).body(ResultEntity.failed("读取图库失败"));
        }
    }

    @GetMapping("gallery/file")
    public ResponseEntity<Resource> getGalleryFile(@RequestParam String artistDirectory,
                                                   @RequestParam String path,
                                                   @RequestParam(required = false,
                                                           defaultValue = "false") boolean thumbnail) {
        if (path == null || path.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path directory = artistDirectory(artistDirectory);
        if (directory == null || !Files.isDirectory(directory)) {
            return ResponseEntity.notFound().build();
        }
        Path requested = resolveMedia(directory, path);
        if (requested == null) {
            return ResponseEntity.notFound().build();
        }
        Path servedFile = requested;
        if (thumbnail && isImage(requested)) {
            try {
                servedFile = cachedThumbnail(requested, artistDirectory);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to create gallery thumbnail for {}", requested, exception);
                servedFile = requested;
            }
        }
        FileSystemResource resource = new FileSystemResource(servedFile);
        MediaType mediaType = mediaType(servedFile);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .header("Accept-Ranges", "bytes")
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate());
        try {
            response.contentLength(resource.contentLength());
        } catch (IOException ignored) {
            // The resource can still be streamed when its size is unavailable.
        }
        return response.body(resource);
    }

    private ResponseEntity<ResultEntity<ArtistGalleryData>> galleryResponse(
            ArtistManagementItem artist, List<ArtistGalleryItem> images) {
        return ResponseEntity.ok(ResultEntity.successWithData(new ArtistGalleryData(artist, images)));
    }

    private Comparator<ArtistGalleryItem> galleryComparator(String sortBy, String order) {
        boolean ascending = "asc".equalsIgnoreCase(order);
        if ("album".equalsIgnoreCase(sortBy)) {
            return (left, right) -> compareAlbumItems(left, right, ascending);
        }
        Comparator<ArtistGalleryItem> comparator = "name".equalsIgnoreCase(sortBy)
                ? Comparator.comparing(ArtistGalleryItem::getName, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingLong(ArtistGalleryItem::getCreatedAt);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(ArtistGalleryItem::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private int compareAlbumItems(ArtistGalleryItem left, ArtistGalleryItem right, boolean ascending) {
        LocalDate leftDate = leadingDate(left.getName());
        LocalDate rightDate = leadingDate(right.getName());
        if (leftDate != null && rightDate == null) {
            return -1;
        }
        if (leftDate == null && rightDate != null) {
            return 1;
        }
        if (leftDate != null) {
            int dateComparison = leftDate.compareTo(rightDate);
            if (dateComparison != 0) {
                return ascending ? dateComparison : -dateComparison;
            }
        }
        return String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName());
    }

    private LocalDate leadingDate(String fileName) {
        Matcher matcher = LEADING_DATE.matcher(fileName == null ? "" : fileName);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private ArtistGalleryItem toGalleryItem(ArtistGalleryCache.CachedImage image, String encodedDirectory)
            throws IOException {
        String encodedPath = URLEncoder.encode(image.getName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        String url = "/api/fanbox/gallery/file?artistDirectory=" + encodedDirectory
                + "&path=" + encodedPath;
        String type = "video".equals(image.getType()) ? "video" : "image";
        String thumbnailUrl = "image".equals(type) ? url + "&thumbnail=true" : null;
        return new ArtistGalleryItem(image.getName(), url, thumbnailUrl, type, image.getCreatedAt());
    }

    private String artistDirectoryName(Artist artist) {
        String directoryName = StringUtil.cleanSym(artist.getName());
        if (directoryName.isEmpty()) {
            directoryName = StringUtil.cleanSym(artist.getArtistId());
        }
        return directoryName;
    }

    private Path artistDirectory(String directoryName) {
        if (directoryName == null || directoryName.trim().isEmpty()) {
            return null;
        }
        try {
            Path relative = Paths.get(directoryName);
            if (relative.isAbsolute() || relative.getNameCount() != 1) {
                return null;
            }
            Path directory = downloadRoot.resolve(relative).normalize();
            return downloadRoot.equals(directory.getParent()) ? directory : null;
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private Path resolveMedia(Path artistDirectory, String fileName) {
        try {
            Path relative = Paths.get(fileName);
            if (relative.isAbsolute() || relative.getNameCount() != 1) {
                return null;
            }
            Path requested = artistDirectory.resolve(relative).normalize();
            return artistDirectory.equals(requested.getParent())
                    && Files.isRegularFile(requested) && isSupportedMedia(requested) ? requested : null;
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private Path cachedThumbnail(Path source, String artistDirectoryName) throws IOException {
        String directoryCacheKey = stableCacheKey(artistDirectoryName);
        String imageCacheKey = stableCacheKey(source.getFileName().toString()) + ".jpg";
        Path target = thumbnailCacheRoot.resolve(directoryCacheKey).resolve(imageCacheKey).normalize();
        if (!target.startsWith(thumbnailCacheRoot)) {
            throw new IOException("缩略图缓存路径无效");
        }
        synchronized (thumbnailLock(target)) {
            if (isFreshThumbnail(source, target)) {
                return target;
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "thumbnail-", ".jpg");
            try {
                Thumbnails.of(source.toFile())
                        .size(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT)
                        .outputFormat("jpg")
                        .outputQuality(0.82d)
                        .toFile(temporary.toFile());
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return target;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private boolean isFreshThumbnail(Path source, Path thumbnail) throws IOException {
        return Files.isRegularFile(thumbnail)
                && Files.size(thumbnail) > 0
                && Files.getLastModifiedTime(thumbnail).toMillis()
                >= Files.getLastModifiedTime(source).toMillis();
    }

    private Object thumbnailLock(Path target) {
        return THUMBNAIL_LOCKS[(target.toString().hashCode() & Integer.MAX_VALUE) % THUMBNAIL_LOCKS.length];
    }

    private static Object[] thumbnailLocks() {
        Object[] locks = new Object[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private String stableCacheKey(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    private boolean isImage(Path path) {
        return IMAGE_EXTENSIONS.contains(extension(path));
    }

    private boolean isSupportedMedia(Path path) {
        String extension = extension(path);
        return IMAGE_EXTENSIONS.contains(extension) || VIDEO_EXTENSIONS.contains(extension);
    }

    private String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private MediaType mediaType(Path path) {
        switch (extension(path)) {
            case "mp4":
                return MediaType.parseMediaType("video/mp4");
            case "webm":
                return MediaType.parseMediaType("video/webm");
            case "ogv":
            case "ogg":
                return MediaType.parseMediaType("video/ogg");
            case "mov":
                return MediaType.parseMediaType("video/quicktime");
            case "m4v":
                return MediaType.parseMediaType("video/x-m4v");
            case "mkv":
                return MediaType.parseMediaType("video/x-matroska");
            case "avi":
                return MediaType.parseMediaType("video/x-msvideo");
            default:
                return MediaTypeFactory.getMediaType(path.getFileName().toString())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM);
        }
    }
}

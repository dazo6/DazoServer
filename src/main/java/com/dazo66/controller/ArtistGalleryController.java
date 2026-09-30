package com.dazo66.controller;

import com.dazo66.dashboard.ArtistGalleryData;
import com.dazo66.dashboard.ArtistGalleryItem;
import com.dazo66.service.ArtistGalleryCache;
import com.dazo66.service.FreeGalleryService;
import com.dazo66.util.ResultEntity;
import net.coobird.thumbnailator.Thumbnails;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves downloaded images and videos for a folder-based gallery.
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
    private static final Semaphore VIDEO_THUMBNAIL_SLOTS = new Semaphore(2);
    private static final int THUMBNAIL_MAX_WIDTH = 600;
    private static final int THUMBNAIL_MAX_HEIGHT = 1200;
    private static final Pattern LEADING_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    private final ArtistGalleryCache galleryCache;
    private final FreeGalleryService freeGalleryService;
    private final Path downloadRoot;
    private final Path thumbnailCacheRoot;

    @Autowired
    public ArtistGalleryController(@Value("${fanbox.save.path}") String downloadPath,
                                   ArtistGalleryCache galleryCache,
                                   FreeGalleryService freeGalleryService) {
        this(downloadPath,
                Paths.get("").toAbsolutePath().normalize().resolve("cache").resolve("gallery-thumbnails"),
                galleryCache, freeGalleryService);
    }

    ArtistGalleryController(String downloadPath,
                            Path thumbnailCacheRoot) {
        this(downloadPath, thumbnailCacheRoot,
                new ArtistGalleryCache(Paths.get(downloadPath),
                        thumbnailCacheRoot.resolveSibling("gallery-indexes"), new com.fasterxml.jackson.databind.ObjectMapper()),
                null);
    }

    ArtistGalleryController(String downloadPath,
                            Path thumbnailCacheRoot,
                            ArtistGalleryCache galleryCache) {
        this(downloadPath, thumbnailCacheRoot, galleryCache, null);
    }

    ArtistGalleryController(String downloadPath,
                            Path thumbnailCacheRoot,
                            ArtistGalleryCache galleryCache,
                            FreeGalleryService freeGalleryService) {
        this.galleryCache = galleryCache;
        this.freeGalleryService = freeGalleryService;
        this.downloadRoot = Paths.get(downloadPath).toAbsolutePath().normalize();
        this.thumbnailCacheRoot = thumbnailCacheRoot.toAbsolutePath().normalize();
    }

    @GetMapping("gallery")
    public ResponseEntity<ResultEntity<ArtistGalleryData>> getGallery(
            @RequestParam String folderPath,
            @RequestParam(required = false, defaultValue = "album") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        Path directory = galleryDirectory(folderPath);
        if (directory == null) {
            return ResponseEntity.notFound().build();
        }
        String normalizedFolderPath = directory.toString();
        try {
            List<ArtistGalleryCache.CachedImage> cachedImages = galleryCache.getOrBuildAuthorized(normalizedFolderPath);
            List<ArtistGalleryItem> items = new ArrayList<>(cachedImages.size());
            for (ArtistGalleryCache.CachedImage cachedImage : cachedImages) {
                items.add(toGalleryItem(cachedImage, directory));
            }
            items.sort(galleryComparator(sortBy, order));
            return galleryResponse(normalizedFolderPath, items);
        } catch (IOException exception) {
            LOGGER.warn("Unable to read gallery folder {}", normalizedFolderPath, exception);
            return ResponseEntity.status(500).body(ResultEntity.failed("读取图库失败"));
        }
    }

    @GetMapping("gallery/file")
    public ResponseEntity<Resource> getGalleryFile(@RequestParam String folderPath,
                                                   @RequestParam String path,
                                                   @RequestParam(required = false,
                                                           defaultValue = "false") boolean thumbnail) {
        if (path == null || path.trim().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path directory = galleryDirectory(folderPath);
        if (directory == null) {
            return ResponseEntity.notFound().build();
        }
        Path requested = resolveGalleryEntry(directory, path);
        if (requested == null) {
            return ResponseEntity.notFound().build();
        }
        Path servedFile;
        if (Files.isDirectory(requested)) {
            servedFile = galleryCache.posterFile(requested.toString());
            if (!Files.isRegularFile(servedFile)) {
                return ResponseEntity.notFound().build();
            }
        } else {
            servedFile = requested;
        }
        if (thumbnail && Files.isRegularFile(requested) && isImage(requested)) {
            try {
                servedFile = cachedThumbnail(requested, directory.toString());
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Unable to create gallery thumbnail for {}", requested, exception);
                servedFile = requested;
            }
        } else if (thumbnail && Files.isRegularFile(requested) && isVideo(requested)) {
            try {
                servedFile = cachedVideoThumbnail(requested, directory.toString());
            } catch (IOException exception) {
                LOGGER.warn("无法生成视频首帧缩略图: {}", requested, exception);
                return ResponseEntity.status(500).build();
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
            String folderPath, List<ArtistGalleryItem> images) {
        return ResponseEntity.ok(ResultEntity.successWithData(new ArtistGalleryData(folderPath, images)));
    }

    private Comparator<ArtistGalleryItem> galleryComparator(String sortBy, String order) {
        boolean ascending = "asc".equalsIgnoreCase(order);
        Comparator<ArtistGalleryItem> foldersFirst = Comparator.comparing(
                item -> !"folder".equals(item.getType()));
        Comparator<ArtistGalleryItem> itemComparator;
        if ("album".equalsIgnoreCase(sortBy)) {
            itemComparator = (left, right) -> compareAlbumItems(left, right, ascending);
            return foldersFirst.thenComparing(itemComparator);
        }
        itemComparator = "name".equalsIgnoreCase(sortBy)
                ? Comparator.comparing(ArtistGalleryItem::getName, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparingLong(ArtistGalleryItem::getCreatedAt);
        if (!ascending) {
            itemComparator = itemComparator.reversed();
        }
        return foldersFirst.thenComparing(itemComparator)
                .thenComparing(ArtistGalleryItem::getName, String.CASE_INSENSITIVE_ORDER);
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

    private ArtistGalleryItem toGalleryItem(ArtistGalleryCache.CachedImage image, Path directory)
            throws IOException {
        if ("folder".equals(image.getType())) {
            Path childDirectory = directory.resolve(image.getName()).normalize();
            ArtistGalleryItem folder = new ArtistGalleryItem(image.getName(), null, null, "folder",
                    childDirectory.toString(), image.getCreatedAt());
            folder.setPosterUrl("/api/fanbox/gallery/file?folderPath=" + encode(directory.toString())
                    + "&path=" + encode(image.getName()));
            return folder;
        }
        String encodedFolderPath = encode(directory.toString());
        String encodedPath = URLEncoder.encode(image.getName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        String url = "/api/fanbox/gallery/file?folderPath=" + encodedFolderPath
                + "&path=" + encodedPath;
        String type = "video".equals(image.getType()) ? "video" : "image";
        String thumbnailUrl = url + "&thumbnail=true";
        return new ArtistGalleryItem(image.getName(), url, thumbnailUrl, type, image.getCreatedAt());
    }

    private Path galleryDirectory(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return null;
        }
        try {
            Path directory = Paths.get(folderPath);
            if (!directory.isAbsolute()) {
                return null;
            }
            directory = directory.toAbsolutePath().normalize();
            if (!Files.isDirectory(directory)) {
                return null;
            }
            Path realRoot = Files.isDirectory(downloadRoot) ? downloadRoot.toRealPath() : downloadRoot;
            Path realDirectory = directory.toRealPath();
            boolean inDownloadRoot = realDirectory.startsWith(realRoot);
            boolean inRegisteredGallery = freeGalleryService != null
                    && freeGalleryService.isRegisteredDirectory(realDirectory);
            return inDownloadRoot || inRegisteredGallery ? realDirectory : null;
        } catch (InvalidPathException | IOException exception) {
            return null;
        }
    }

    private Path resolveGalleryEntry(Path galleryDirectory, String fileName) {
        try {
            Path relative = Paths.get(fileName);
            if (relative.isAbsolute() || relative.getNameCount() != 1) {
                return null;
            }
            Path requested = galleryDirectory.resolve(relative).normalize();
            if (!galleryDirectory.equals(requested.getParent())
                    || (!Files.isRegularFile(requested) && !Files.isDirectory(requested))) {
                return null;
            }
            Path realRequested = requested.toRealPath();
            return galleryDirectory.equals(realRequested.getParent())
                    && (Files.isDirectory(realRequested) || isSupportedMedia(realRequested))
                    ? realRequested : null;
        } catch (InvalidPathException | IOException exception) {
            return null;
        }
    }

    private Path cachedThumbnail(Path source, String folderPath) throws IOException {
        Path target = thumbnailFile(source, folderPath);
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

    private Path cachedVideoThumbnail(Path source, String folderPath) throws IOException {
        Path target = thumbnailFile(source, folderPath);
        synchronized (thumbnailLock(target)) {
            if (isFreshThumbnail(source, target)) {
                return target;
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "video-thumbnail-", ".jpg");
            try {
                extractVideoFrame(source, temporary);
                if (Files.size(temporary) == 0) {
                    throw new IOException("视频首帧为空");
                }
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

    private Path thumbnailFile(Path source, String folderPath) throws IOException {
        String directoryCacheKey = stableCacheKey(folderPath);
        String imageCacheKey = stableCacheKey(source.getFileName().toString()) + ".jpg";
        Path target = thumbnailCacheRoot.resolve(directoryCacheKey).resolve(imageCacheKey).normalize();
        if (!target.startsWith(thumbnailCacheRoot)) {
            throw new IOException("缩略图缓存路径无效");
        }
        return target;
    }

    void extractVideoFrame(Path source, Path target) throws IOException {
        boolean slotAcquired;
        try {
            slotAcquired = VIDEO_THUMBNAIL_SLOTS.tryAcquire(60, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("等待视频首帧提取时被中断");
        }
        if (!slotAcquired) {
            throw new IOException("等待视频首帧提取超时");
        }
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new IOException("视频没有可提取的画面");
            }
            BufferedImage image = converter.convert(frame);
            if (image == null) {
                throw new IOException("无法转换视频首帧");
            }
            Thumbnails.of(image)
                    .size(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT)
                    .outputFormat("jpg")
                    .outputQuality(0.82d)
                    .toFile(target.toFile());
        } catch (FrameGrabber.Exception | RuntimeException exception) {
            throw new IOException("无法提取视频首帧: " + source, exception);
        } finally {
            VIDEO_THUMBNAIL_SLOTS.release();
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

    private boolean isVideo(Path path) {
        return VIDEO_EXTENSIONS.contains(extension(path));
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

package com.dazo66.service;

import com.dazo66.entity.Artist;
import com.dazo66.util.StringUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.*;

/**
 * Stores one compact, on-disk JSON gallery index per absolute folder path.
 */
@Service
public class ArtistGalleryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtistGalleryCache.class);
    private static final int CACHE_VERSION = 8;
    private static final int POSTER_IMAGE_MAX_EDGE = 620;
    private static final int POSTER_BORDER = 12;
    private static final int POSTER_PADDING = 20;
    private static final java.util.concurrent.Semaphore POSTER_BUILD_SLOTS = new java.util.concurrent.Semaphore(1, true);
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif", "jfif"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "webm", "mov", "m4v", "ogv", "ogg", "mkv", "avi"
    ));
    private static final Object[] CACHE_LOCKS = cacheLocks();

    private final Path downloadRoot;
    private final Path cacheRoot;
    private final Path posterCacheRoot;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArtistGalleryCache(@Value("${fanbox.save.path}") String downloadPath,
                              @Value("${artist.gallery.cache.path:cache/gallery-indexes}") String cachePath,
                              ObjectMapper objectMapper) {
        this(Paths.get(downloadPath), Paths.get(cachePath), objectMapper);
    }

    public ArtistGalleryCache(Path downloadRoot, Path cacheRoot, ObjectMapper objectMapper) {
        this.downloadRoot = downloadRoot.toAbsolutePath().normalize();
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
        this.posterCacheRoot = this.cacheRoot.resolveSibling("gallery-posters");
        this.objectMapper = objectMapper;
    }

    /**
     * Reads a cached index, rescanning only when the directory entries have changed.
     */
    public List<CachedImage> getOrBuild(String folderPath) throws IOException {
        return getOrBuild(folderPath, false);
    }

    /**
     * Reads a gallery after its path has already been authorized by the caller.
     */
    public List<CachedImage> getOrBuildAuthorized(String folderPath) throws IOException {
        return getOrBuild(folderPath, true);
    }

    private List<CachedImage> getOrBuild(String folderPath, boolean allowExternal) throws IOException {
        Path directory = galleryDirectory(folderPath, allowExternal);
        String normalizedPath = directory.toString();
        Path cacheFile = cacheFile(normalizedPath);
        synchronized (cacheLock(normalizedPath)) {
            List<CachedImage> previousImages = null;
            if (Files.isRegularFile(cacheFile)) {
                try {
                    CacheDocument document = objectMapper.readValue(cacheFile.toFile(), CacheDocument.class);
                    if (Objects.equals(document.directory, normalizedPath) && document.images != null) {
                        previousImages = document.images;
                    }
                    if (document.version == CACHE_VERSION
                            && Objects.equals(document.directory, normalizedPath)
                            && document.images != null) {
                        if (document.directoryModifiedAt == directoryModifiedAt(directory)) {
                            return document.images;
                        }
                        LOGGER.info("图库目录已变化，将更新索引: {}", directory);
                    } else {
                        LOGGER.info("图库缓存版本或目录已变化，将重新生成: {}", cacheFile);
                    }
                } catch (IOException | RuntimeException exception) {
                    LOGGER.warn("图库缓存读取失败，将重新生成: {}", cacheFile, exception);
                }
            }
            return rebuildLocked(directory, normalizedPath, cacheFile, false, previousImages);
        }
    }

    public List<CachedImage> getOrBuild(Artist artist) throws IOException {
        return getOrBuild(artistFolderPath(artist));
    }

    /**
     * Always scans the artist directory and atomically replaces its JSON cache.
     */
    public List<CachedImage> rebuild(String folderPath) throws IOException {
        return rebuild(folderPath, false);
    }

    public List<CachedImage> rebuildAuthorized(String folderPath) throws IOException {
        return rebuild(folderPath, true);
    }

    private List<CachedImage> rebuild(String folderPath, boolean allowExternal) throws IOException {
        Path directory = galleryDirectory(folderPath, allowExternal);
        String normalizedPath = directory.toString();
        Path cacheFile = cacheFile(normalizedPath);
        synchronized (cacheLock(normalizedPath)) {
            return rebuildLocked(directory, normalizedPath, cacheFile, false);
        }
    }

    public List<CachedImage> rebuild(Artist artist) throws IOException {
        return rebuild(artistFolderPath(artist));
    }

    /**
     * Scheduled refresh includes folder posters; request-time cache rebuilds stay shallow.
     */
    public void rebuildScheduledTree(String folderPath) throws IOException {
        Path root = galleryDirectory(folderPath, true);
        if (!Files.isDirectory(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                try {
                    String normalizedPath = directory.toAbsolutePath().normalize().toString();
                    synchronized (cacheLock(normalizedPath)) {
                        rebuildLocked(directory, normalizedPath, cacheFile(normalizedPath), true);
                    }
                } catch (IOException exception) {
                    LOGGER.warn("定时生成图库索引失败: {}", directory, exception);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                LOGGER.warn("跳过无法访问的图库目录或文件: {}", file, exception);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void rebuildScheduledTree(Artist artist) throws IOException {
        rebuildScheduledTree(artistFolderPath(artist));
    }

    private List<CachedImage> rebuildLocked(Path directory, String folderPath, Path cacheFile)
            throws IOException {
        return rebuildLocked(directory, folderPath, cacheFile, false);
    }

    private List<CachedImage> rebuildLocked(Path directory, String folderPath, Path cacheFile,
                                            boolean includeFolderPosters) throws IOException {
        return rebuildLocked(directory, folderPath, cacheFile, includeFolderPosters, null);
    }

    private List<CachedImage> rebuildLocked(Path directory, String folderPath, Path cacheFile,
                                            boolean includeFolderPosters, List<CachedImage> previousImages)
            throws IOException {
        long directoryModifiedAt = directoryModifiedAt(directory);
        List<CachedImage> images = scan(directory, includeFolderPosters, previousImages);
        // Deterministic files make cache inspection and replacement easier. Request-specific sorting remains in the controller.
        images.sort(Comparator.comparing(CachedImage::getName, String.CASE_INSENSITIVE_ORDER));
        writeAtomically(cacheFile, new CacheDocument(CACHE_VERSION, folderPath, directoryModifiedAt, images));
        LOGGER.info("图库缓存已生成: folderPath={}, images={}, file={}", folderPath, images.size(), cacheFile);
        return images;
    }

    private long directoryModifiedAt(Path directory) throws IOException {
        return Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS).toMillis();
    }

    private List<CachedImage> scan(Path directory, boolean includeFolderPosters,
                                   List<CachedImage> previousImages) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return new ArrayList<>();
        }
        List<CachedImage> images = new ArrayList<>();
        Map<String, String> previousPosters = new HashMap<>();
        if (previousImages != null) {
            for (CachedImage previous : previousImages) {
                if ("folder".equals(previous.getType()) && previous.getPosterName() != null) {
                    previousPosters.put(previous.getName(), previous.getPosterName());
                }
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isDirectory()) {
                        CachedImage folder = new CachedImage(file.getFileName().toString(), "folder",
                                attributes.creationTime().toMillis());
                        if (includeFolderPosters) {
                            folder.setPosterName(buildFolderPoster(file));
                        } else if (previousPosters.containsKey(folder.getName())
                                && Files.isRegularFile(posterFile(file.toString()))) {
                            folder.setPosterName(previousPosters.get(folder.getName()));
                        }
                        images.add(folder);
                        continue;
                    }
                    String type = mediaType(file);
                    if (type != null && attributes.isRegularFile()) {
                        images.add(new CachedImage(file.getFileName().toString(), type,
                                attributes.creationTime().toMillis()));
                    }
                } catch (IOException exception) {
                    LOGGER.warn("跳过无法读取属性的图库文件: {}", file, exception);
                }
            }
        }
        return images;
    }

    private String buildFolderPoster(Path folder) {
        try {
            POSTER_BUILD_SLOTS.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            return generateFolderPoster(folder);
        } finally {
            POSTER_BUILD_SLOTS.release();
        }
    }

    private String generateFolderPoster(Path folder) {
        List<String> names = folderPosterNames(folder);
        if (names == null) {
            return null;
        }
        Path target = posterFile(folder.toAbsolutePath().normalize().toString());
        List<BufferedImage> selected = new ArrayList<>(3);
        try {
            for (String name : names) {
                try {
                    selected.add(readPosterImage(folder.resolve(name)));
                    if (selected.size() == 3) break;
                } catch (IOException exception) {
                    LOGGER.debug("跳过无法解码的文件夹封面图片: {}", folder.resolve(name), exception);
                }
            }
            if (selected.size() < 3) {
                LOGGER.warn("无法生成文件夹海报，少于三张可解码图片: {}", folder);
                return null;
            }
            BufferedImage first = selected.get(0);
            double scale = (double) POSTER_IMAGE_MAX_EDGE / Math.max(first.getWidth(), first.getHeight());
            int imageWidth = Math.max(1, (int) Math.round(first.getWidth() * scale));
            int imageHeight = Math.max(1, (int) Math.round(first.getHeight() * scale));
            int sheetWidth = imageWidth + POSTER_BORDER * 2;
            int sheetHeight = imageHeight + POSTER_BORDER * 2;
            Rectangle2D bounds = posterSheetBounds(sheetWidth, sheetHeight, 0, 0, 0);
            bounds = bounds.createUnion(posterSheetBounds(sheetWidth, sheetHeight, -20, -28, -8));
            bounds = bounds.createUnion(posterSheetBounds(sheetWidth, sheetHeight, 20, -24, 8));
            int width = (int) Math.ceil(bounds.getWidth()) + POSTER_PADDING * 2;
            int height = (int) Math.ceil(bounds.getHeight()) + POSTER_PADDING * 2;
            BufferedImage poster = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = poster.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.translate(POSTER_PADDING - bounds.getMinX(), POSTER_PADDING - bounds.getMinY());
                drawPosterSheet(graphics, selected.get(2), -20, -28, -8, sheetWidth, sheetHeight, false);
                drawPosterSheet(graphics, selected.get(1), 20, -24, 8, sheetWidth, sheetHeight, false);
                drawPosterSheet(graphics, first, 0, 0, 0, sheetWidth, sheetHeight, true);
            } finally {
                graphics.dispose();
            }
            Files.createDirectories(posterCacheRoot);
            Path temporary = Files.createTempFile(posterCacheRoot, "gallery-poster-", ".png");
            try {
                if (!ImageIO.write(poster, "png", temporary.toFile())) {
                    throw new IOException("没有可用的 PNG 编码器");
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return target.getFileName().toString();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("无法生成文件夹海报: {}", folder, exception);
            return null;
        } finally {
            selected.forEach(BufferedImage::flush);
        }
    }

    private BufferedImage readPosterImage(Path source) throws IOException {
        if (Files.size(source) < 12) {
            throw new IOException("封面图片文件过短: " + source);
        }
        try {
            BufferedImage image = ImageIO.read(source.toFile());
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) return image;
        } catch (IOException | RuntimeException exception) {
            LOGGER.debug("ImageIO 无法解码文件夹封面图片，尝试 FFmpeg: {}", source, exception);
        }
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            Frame frame = grabber.grabImage();
            BufferedImage decoded = frame == null ? null : converter.convert(frame);
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
                throw new IOException("无法解码封面图片: " + source);
            }
            BufferedImage image = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return image;
        } catch (FrameGrabber.Exception | RuntimeException exception) {
            throw new IOException("无法解码封面图片: " + source, exception);
        }
    }

    private Rectangle2D posterSheetBounds(int width, int height, int x, int y, int degrees) {
        AffineTransform rotation = AffineTransform.getRotateInstance(Math.toRadians(degrees),
                x + width / 2.0, y + height / 2.0);
        return rotation.createTransformedShape(new Rectangle2D.Double(x, y, width + 9, height + 12)).getBounds2D();
    }

    private void drawPosterSheet(Graphics2D graphics, BufferedImage image, int x, int y, int degrees,
                                 int width, int height, boolean preserveWholeImage) {
        final int border = POSTER_BORDER;
        AffineTransform transform = graphics.getTransform();
        Shape clip = graphics.getClip();
        try {
            graphics.rotate(Math.toRadians(degrees), x + width / 2.0, y + height / 2.0);
            graphics.setColor(new Color(45, 57, 76, 75));
            graphics.fill(new RoundRectangle2D.Double(x + 9, y + 12, width, height, 16, 16));
            graphics.setColor(Color.WHITE);
            graphics.fill(new RoundRectangle2D.Double(x, y, width, height, 16, 16));
            int left = x + border;
            int top = y + border;
            int innerWidth = width - border * 2;
            int innerHeight = height - border * 2;
            graphics.clip(new Rectangle2D.Double(left, top, innerWidth, innerHeight));
            double sourceRatio = (double) image.getWidth() / image.getHeight();
            double targetRatio = (double) innerWidth / innerHeight;
            int cropWidth = !preserveWholeImage && sourceRatio > targetRatio
                    ? Math.max(1, (int) Math.round(image.getHeight() * targetRatio)) : image.getWidth();
            int cropHeight = preserveWholeImage || sourceRatio > targetRatio ? image.getHeight()
                    : Math.max(1, (int) Math.round(image.getWidth() / targetRatio));
            int sourceX = (image.getWidth() - cropWidth) / 2;
            int sourceY = (image.getHeight() - cropHeight) / 2;
            graphics.drawImage(image, left, top, left + innerWidth, top + innerHeight,
                    sourceX, sourceY, sourceX + cropWidth, sourceY + cropHeight, null);
        } finally {
            graphics.setClip(clip);
            graphics.setTransform(transform);
        }
    }

    private List<String> folderPosterNames(Path folder) {
        NavigableSet<String> names = new TreeSet<>(
                String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && "image".equals(mediaType(file))) {
                    names.add(file.getFileName().toString());
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("无法读取文件夹封面: {}", folder, exception);
            return null;
        }
        return names.size() >= 3 ? new ArrayList<>(names) : null;
    }

    /**
     * Returns a cache-only PNG path; no poster is written inside a gallery folder.
     */
    public Path posterFile(String folderPath) {
        Path folder = Paths.get(folderPath).toAbsolutePath().normalize();
        String key = UUID.nameUUIDFromBytes(folder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
        return posterCacheRoot.resolve("folder-" + key + "-v2.png");
    }

    /** Removes obsolete covers without opening gallery source files. */
    @javax.annotation.PostConstruct
    public void removeLegacyFolderPosters() throws IOException {
        if (!Files.isDirectory(posterCacheRoot, LinkOption.NOFOLLOW_LINKS)) return;
        int removed = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(posterCacheRoot)) {
            for (Path file : files) {
                if (file.getFileName().toString().matches("folder-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.png")
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.deleteIfExists(file)) removed++;
                }
            }
        }
        LOGGER.info("Removed {} obsolete gallery folder posters", removed);
    }

    private void writeAtomically(Path target, CacheDocument document) throws IOException {
        Files.createDirectories(cacheRoot);
        Path temporary = Files.createTempFile(cacheRoot, "gallery-index-", ".tmp");
        try {
            objectMapper.writeValue(temporary.toFile(), document);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path cacheFile(String folderPath) {
        String key = UUID.nameUUIDFromBytes(folderPath.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return cacheRoot.resolve("folder-" + key + ".json");
    }

    private String artistFolderPath(Artist artist) throws IOException {
        if (artist == null) {
            throw new IOException("画师为空，无法创建图库缓存");
        }
        String directoryName = StringUtil.cleanSym(artist.getName());
        if (directoryName.isEmpty()) {
            directoryName = StringUtil.cleanSym(artist.getArtistId());
        }
        return downloadRoot.resolve(directoryName).normalize().toString();
    }

    private Path galleryDirectory(String folderPath, boolean allowExternal) throws IOException {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            throw new IOException("文件夹路径为空");
        }
        try {
            Path directory = Paths.get(folderPath);
            if (!directory.isAbsolute()) {
                throw new IOException("图库必须使用绝对文件夹路径");
            }
            directory = directory.toAbsolutePath().normalize();
            if (!allowExternal && !directory.startsWith(downloadRoot)) {
                throw new IOException("图库文件夹不在下载目录内");
            }
            return directory;
        } catch (InvalidPathException exception) {
            throw new IOException("文件夹路径无效", exception);
        }
    }

    private String mediaType(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return "image";
        }
        return VIDEO_EXTENSIONS.contains(extension) ? "video" : null;
    }

    private Object cacheLock(String folderPath) {
        int hash = folderPath == null ? 0 : folderPath.hashCode();
        return CACHE_LOCKS[(hash & Integer.MAX_VALUE) % CACHE_LOCKS.length];
    }

    private static Object[] cacheLocks() {
        Object[] locks = new Object[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    public static final class CachedImage {
        private String name;
        private String type;
        private long createdAt;
        private String posterName;

        public CachedImage() {
        }

        public CachedImage(String name, long createdAt) {
            this(name, "image", createdAt);
        }

        public CachedImage(String name, String type, long createdAt) {
            this.name = name;
            this.type = type;
            this.createdAt = createdAt;
        }

        @JsonProperty("n")
        public String getName() {
            return name;
        }

        @JsonProperty("n")
        public void setName(String name) {
            this.name = name;
        }

        @JsonProperty("m")
        public String getType() {
            return type;
        }

        @JsonProperty("m")
        public void setType(String type) {
            this.type = type;
        }

        @JsonProperty("t")
        public long getCreatedAt() {
            return createdAt;
        }

        @JsonProperty("t")
        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        @JsonProperty("p")
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public String getPosterName() {
            return posterName;
        }

        @JsonProperty("p")
        public void setPosterName(String posterName) {
            this.posterName = posterName;
        }
    }

    private static final class CacheDocument {
        @JsonProperty("v")
        private int version;
        @JsonProperty("d")
        private String directory;
        @JsonProperty("u")
        private long directoryModifiedAt;
        @JsonProperty("i")
        private List<CachedImage> images;

        public CacheDocument() {
        }

        private CacheDocument(int version, String directory, long directoryModifiedAt,
                              List<CachedImage> images) {
            this.version = version;
            this.directory = directory;
            this.directoryModifiedAt = directoryModifiedAt;
            this.images = images;
        }
    }
}

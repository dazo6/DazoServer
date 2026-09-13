package com.dazo66.service;

import com.dazo66.entity.Artist;
import com.dazo66.util.StringUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Stores one compact, on-disk JSON gallery index per artist.
 */
@Service
public class ArtistGalleryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtistGalleryCache.class);
    private static final int CACHE_VERSION = 1;
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif", "jfif"
    ));
    private static final Object[] CACHE_LOCKS = cacheLocks();

    private final Path downloadRoot;
    private final Path cacheRoot;
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
        this.objectMapper = objectMapper;
    }

    /**
     * Reads an existing cache without touching the artist directory. Missing, invalid or obsolete caches are rebuilt.
     */
    public List<CachedImage> getOrBuild(Artist artist) throws IOException {
        String directoryName = artistDirectoryName(artist);
        Path cacheFile = cacheFile(artist);
        synchronized (cacheLock(artist)) {
            if (Files.isRegularFile(cacheFile)) {
                try {
                    CacheDocument document = objectMapper.readValue(cacheFile.toFile(), CacheDocument.class);
                    if (document.version == CACHE_VERSION
                            && Objects.equals(document.directory, directoryName)
                            && document.images != null) {
                        return document.images;
                    }
                    LOGGER.info("图库缓存版本或目录已变化，将重新生成: {}", cacheFile);
                } catch (IOException | RuntimeException exception) {
                    LOGGER.warn("图库缓存读取失败，将重新生成: {}", cacheFile, exception);
                }
            }
            return rebuildLocked(artist, directoryName, cacheFile);
        }
    }

    /**
     * Always scans the artist directory and atomically replaces its JSON cache.
     */
    public List<CachedImage> rebuild(Artist artist) throws IOException {
        String directoryName = artistDirectoryName(artist);
        Path cacheFile = cacheFile(artist);
        synchronized (cacheLock(artist)) {
            return rebuildLocked(artist, directoryName, cacheFile);
        }
    }

    private List<CachedImage> rebuildLocked(Artist artist, String directoryName, Path cacheFile)
            throws IOException {
        Path directory = artistDirectory(directoryName);
        List<CachedImage> images = scan(directory);
        // Deterministic files make cache inspection and replacement easier. Request-specific sorting remains in the controller.
        images.sort(Comparator.comparing(CachedImage::getName, String.CASE_INSENSITIVE_ORDER));
        writeAtomically(cacheFile, new CacheDocument(CACHE_VERSION, directoryName, images));
        LOGGER.info("图库缓存已生成: artistId={}, images={}, file={}", artist.getId(), images.size(), cacheFile);
        return images;
    }

    private List<CachedImage> scan(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return new ArrayList<>();
        }
        List<CachedImage> images = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!isImage(file)) {
                    continue;
                }
                try {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attributes.isRegularFile()) {
                        images.add(new CachedImage(file.getFileName().toString(),
                                attributes.creationTime().toMillis()));
                    }
                } catch (IOException exception) {
                    LOGGER.warn("跳过无法读取属性的图库文件: {}", file, exception);
                }
            }
        }
        return images;
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

    private Path cacheFile(Artist artist) throws IOException {
        if (artist == null || artist.getId() == null) {
            throw new IOException("画师 ID 为空，无法创建图库缓存");
        }
        return cacheRoot.resolve("artist-" + artist.getId() + ".json");
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

    private boolean isImage(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private Object cacheLock(Artist artist) {
        int id = artist == null || artist.getId() == null ? 0 : artist.getId();
        return CACHE_LOCKS[(id & Integer.MAX_VALUE) % CACHE_LOCKS.length];
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
        private long createdAt;

        public CachedImage() {
        }

        public CachedImage(String name, long createdAt) {
            this.name = name;
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

        @JsonProperty("t")
        public long getCreatedAt() {
            return createdAt;
        }

        @JsonProperty("t")
        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }
    }

    private static final class CacheDocument {
        @JsonProperty("v")
        private int version;
        @JsonProperty("d")
        private String directory;
        @JsonProperty("i")
        private List<CachedImage> images;

        public CacheDocument() {
        }

        private CacheDocument(int version, String directory, List<CachedImage> images) {
            this.version = version;
            this.directory = directory;
            this.images = images;
        }
    }
}

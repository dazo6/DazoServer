package com.dazo66.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistGalleryCacheTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void refreshesCompactJsonCacheWhenDirectoryEntriesChange() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("downloads");
        Path artistDirectory = downloadRoot.resolve("artist-one");
        Path cacheRoot = temporaryDirectory.resolve("cache").resolve("gallery-indexes");
        Files.createDirectories(artistDirectory);
        Files.write(artistDirectory.resolve("first.png"), new byte[]{1});
        Files.write(artistDirectory.resolve("clip.mp4"), new byte[]{3});
        Files.write(artistDirectory.resolve("ignored.txt"), new byte[]{2});
        Files.createDirectories(artistDirectory.resolve("nested"));

        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot, cacheRoot, new ObjectMapper());

        List<ArtistGalleryCache.CachedImage> first = cache.getOrBuild(artistDirectory.toString());
        Path cacheFile;
        try (java.util.stream.Stream<Path> files = Files.list(cacheRoot)) {
            cacheFile = files.filter(Files::isRegularFile).findFirst().get();
        }
        String json = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);

        assertThat(first).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("clip.mp4", "first.png", "nested");
        assertThat(first).extracting(ArtistGalleryCache.CachedImage::getType)
                .containsExactly("video", "image", "folder");
        assertThat(json).doesNotContain("\n").contains("\"v\":8", "\"i\":", "\"m\":\"folder\"");
        assertThat(json).doesNotContain("\"p\":");

        Files.delete(artistDirectory.resolve("first.png"));
        Files.delete(artistDirectory.resolve("clip.mp4"));
        Files.write(artistDirectory.resolve("second.jpeg"), new byte[]{3});
        Files.setLastModifiedTime(artistDirectory, FileTime.fromMillis(
                Files.getLastModifiedTime(artistDirectory).toMillis() + 2000));

        assertThat(cache.getOrBuild(artistDirectory.toString())).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("nested", "second.jpeg");
        assertThat(cache.rebuild(artistDirectory.toString())).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("nested", "second.jpeg");
    }

    @Test
    void rebuildsUnreadableCache() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("broken-downloads");
        Path artistDirectory = downloadRoot.resolve("artist-two");
        Path cacheRoot = temporaryDirectory.resolve("broken-cache");
        Files.createDirectories(artistDirectory);
        Files.createDirectories(cacheRoot);
        Files.write(artistDirectory.resolve("valid.webp"), new byte[]{1});
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot, cacheRoot, new ObjectMapper());
        cache.getOrBuild(artistDirectory.toString());
        Path cacheFile;
        try (java.util.stream.Stream<Path> files = Files.list(cacheRoot)) {
            cacheFile = files.filter(Files::isRegularFile).findFirst().get();
        }
        Files.write(cacheFile, "not-json".getBytes(StandardCharsets.UTF_8));

        assertThat(cache.getOrBuild(artistDirectory.toString())).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("valid.webp");
        assertThat(new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8))
                .startsWith("{\"v\":8");
    }

    @Test
    void scheduledIndexBuildsSingleCacheOnlyPosterForNestedFolders() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("poster-downloads");
        Path nested = downloadRoot.resolve("album").resolve("chapter");
        Files.createDirectories(nested);
        writeImage(nested.resolve("b.jpg"), Color.GREEN);
        writeImage(nested.resolve("a.png"), Color.RED);
        writeImage(nested.resolve("c.jpg"), Color.BLUE);
        writeImage(nested.resolve("d.jpg"), Color.BLACK);
        Files.write(nested.resolve("movie.mp4"), new byte[]{1});
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot,
                temporaryDirectory.resolve("poster-cache"), new ObjectMapper());

        assertThat(cache.getOrBuild(downloadRoot.resolve("album").toString()).get(0).getPosterName())
                .isNull();

        cache.rebuildScheduledTree(downloadRoot.toString());

        Path poster = cache.posterFile(nested.toString());
        assertThat(cache.getOrBuild(downloadRoot.resolve("album").toString()).get(0).getPosterName())
                .isEqualTo(poster.getFileName().toString());
        Path album = downloadRoot.resolve("album");
        Files.write(album.resolve("new.jpeg"), new byte[]{1});
        Files.setLastModifiedTime(album, FileTime.fromMillis(
                Files.getLastModifiedTime(album).toMillis() + 2000));
        assertThat(cache.getOrBuild(album.toString())).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("chapter", "new.jpeg");
        assertThat(cache.getOrBuild(album.toString()).get(0).getPosterName())
                .isEqualTo(poster.getFileName().toString());
        assertThat(cache.getOrBuild(downloadRoot.toString()).get(0).getPosterName()).isNull();
        assertThat(poster).isRegularFile();
        assertThat(poster.getFileName().toString()).endsWith(".png");
        assertThat(poster.getParent()).isEqualTo(temporaryDirectory.resolve("gallery-posters"));
        try (java.util.stream.Stream<Path> sourceFiles = Files.list(nested)) {
            assertThat(sourceFiles.map(Path::getFileName).map(Path::toString))
                    .doesNotContain(poster.getFileName().toString());
        }
        BufferedImage image = ImageIO.read(poster.toFile());
        assertThat(image.getWidth()).isGreaterThan(image.getHeight()).isLessThan(950);
        assertThat(image.getHeight()).isLessThan(950);
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(image.getRGB(0, 0), true).getAlpha()).isZero();
        Color front = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
        assertThat(front.getRed()).isGreaterThan(200);
        assertThat(front.getGreen()).isLessThan(80);
        try (java.util.stream.Stream<Path> cacheFiles = Files.list(temporaryDirectory.resolve("poster-cache"))) {
            assertThat(cacheFiles.map(path -> {
                try {
                    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                } catch (java.io.IOException exception) {
                    throw new RuntimeException(exception);
                }
            })).anyMatch(json -> json.contains("\"p\":\"" + poster.getFileName() + "\""));
        }
    }

    @Test
    void scheduledPosterDecodesWebpAndSkipsUnreadableImages() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("webp-downloads");
        Path folder = downloadRoot.resolve("album");
        Files.createDirectories(folder);
        Files.write(folder.resolve("00-broken.webp"), new byte[]{1, 2, 3});
        Files.write(folder.resolve("01.webp"), Base64.getDecoder().decode(
                "UklGRmgAAABXRUJQVlA4IFwAAAAQBgCdASp4AFoAPjEYi0QiIaEQVAAgAwS0hQtbYB+AH4AAAcZAxF5YBrqb3EXlgGupvcReWAa6m2AA/v8UkH//sZj9oz/JK///gzH8GY/gzH/wnqqGBfAAAAAAAA=="));
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(folder.resolve("01.webp").toFile());
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            grabber.start();
            BufferedImage decoded = converter.convert(grabber.grabImage());
            assertThat(decoded).isNotNull();
            Color decodedColor = new Color(decoded.getRGB(60, 45));
            assertThat(decodedColor.getRed()).isGreaterThan(180);
            assertThat(decodedColor.getGreen()).isLessThan(90);
        }
        writeImage(folder.resolve("02.jpg"), Color.GREEN);
        writeImage(folder.resolve("03.jpg"), Color.BLUE);
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot,
                temporaryDirectory.resolve("webp-indexes"), new ObjectMapper());

        cache.rebuildScheduledTree(downloadRoot.toString());

        Path poster = cache.posterFile(folder.toString());
        assertThat(poster).isRegularFile();
        BufferedImage image = ImageIO.read(poster.toFile());
        assertThat(image).isNotNull();
        Color front = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
        assertThat(front.getRed()).isGreaterThan(180);
        assertThat(front.getGreen()).isLessThan(90);
    }

    @Test
    void posterSizeFollowsFirstImageAndPreservesAllFourEdges() throws Exception {
        BufferedImage landscape = buildMarkedPoster("landscape", 480, 240);
        BufferedImage portrait = buildMarkedPoster("portrait", 240, 480);
        assertThat(landscape.getWidth()).isGreaterThan(landscape.getHeight());
        assertThat(portrait.getHeight()).isGreaterThan(portrait.getWidth());
        assertThat(landscape.getWidth()).isNotEqualTo(portrait.getWidth());
        for (BufferedImage poster : new BufferedImage[]{landscape, portrait}) {
            for (Color edge : new Color[]{Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA}) {
                long pixels = 0;
                for (int y = 0; y < poster.getHeight(); y++) for (int x = 0; x < poster.getWidth(); x++) {
                    if (poster.getRGB(x, y) == edge.getRGB()) pixels++;
                }
                assertThat(pixels).as("Visible first-image edge %s", edge).isGreaterThan(500);
            }
        }
    }

    private BufferedImage buildMarkedPoster(String name, int width, int height) throws Exception {
        Path root = temporaryDirectory.resolve(name);
        Path folder = Files.createDirectories(root.resolve("album"));
        BufferedImage first = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = first.createGraphics();
        try {
            graphics.setColor(Color.RED); graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.YELLOW); graphics.fillRect(0, 0, width, height / 10);
            graphics.setColor(Color.CYAN); graphics.fillRect(0, height - height / 10, width, height / 10);
            graphics.setColor(Color.GREEN); graphics.fillRect(0, height / 10, width / 10, height * 8 / 10);
            graphics.setColor(Color.MAGENTA); graphics.fillRect(width - width / 10, height / 10, width / 10, height * 8 / 10);
        } finally { graphics.dispose(); }
        ImageIO.write(first, "png", folder.resolve("01.png").toFile());
        writeImage(folder.resolve("02.png"), Color.BLACK);
        writeImage(folder.resolve("03.png"), Color.BLACK);
        ArtistGalleryCache cache = new ArtistGalleryCache(root, temporaryDirectory.resolve(name + "-indexes"), new ObjectMapper());
        Path legacy = cache.posterFile(folder.toString()).resolveSibling(cache.posterFile(folder.toString()).getFileName().toString().replace("-v2", ""));
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, new byte[]{1, 2, 3});
        Path unrelated = legacy.resolveSibling("keep.png");
        Files.write(unrelated, new byte[]{4});
        cache.removeLegacyFolderPosters();
        assertThat(legacy).doesNotExist();
        assertThat(unrelated).isRegularFile();
        assertThat(cache.posterFile(folder.toString())).doesNotExist();
        cache.rebuildScheduledTree(root.toString());
        Path generated = cache.posterFile(folder.toString());
        assertThat(generated.getFileName().toString()).endsWith("-v2.png");
        cache.removeLegacyFolderPosters();
        assertThat(generated).isRegularFile();
        String previewDirectory = System.getProperty("gallery.poster.previewDir");
        if (previewDirectory != null) {
            Path previews = java.nio.file.Paths.get(previewDirectory);
            Files.createDirectories(previews);
            Files.copy(generated, previews.resolve(name + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] bytes = Files.readAllBytes(generated);
        Files.delete(folder.resolve("01.png"));
        assertThat(generated).isRegularFile();
        assertThat(Files.readAllBytes(generated)).isEqualTo(bytes);
        return ImageIO.read(generated.toFile());
    }

    private void writeImage(Path path, Color color) throws Exception {
        BufferedImage image = new BufferedImage(120, 90, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, path.toString().endsWith(".png") ? "png" : "jpg", path.toFile());
    }
}

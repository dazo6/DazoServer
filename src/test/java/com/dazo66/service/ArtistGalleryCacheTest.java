package com.dazo66.service;

import com.dazo66.entity.Artist;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistGalleryCacheTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsCompactJsonCacheWithoutRescanningDirectory() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("downloads");
        Path artistDirectory = downloadRoot.resolve("artist-one");
        Path cacheRoot = temporaryDirectory.resolve("cache").resolve("gallery-indexes");
        Files.createDirectories(artistDirectory);
        Files.write(artistDirectory.resolve("first.png"), new byte[]{1});
        Files.write(artistDirectory.resolve("clip.mp4"), new byte[]{3});
        Files.write(artistDirectory.resolve("ignored.txt"), new byte[]{2});

        Artist artist = new Artist().setId(42).setName("artist-one").setArtistId("creator-one");
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot, cacheRoot, new ObjectMapper());

        List<ArtistGalleryCache.CachedImage> first = cache.getOrBuild(artist);
        Path cacheFile = cacheRoot.resolve("artist-42.json");
        String json = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);

        assertThat(first).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("clip.mp4", "first.png");
        assertThat(first).extracting(ArtistGalleryCache.CachedImage::getType)
                .containsExactly("video", "image");
        assertThat(json).doesNotContain("\n").contains("\"v\":2", "\"i\":", "\"m\":\"video\"");

        Files.delete(artistDirectory.resolve("first.png"));
        Files.delete(artistDirectory.resolve("clip.mp4"));
        Files.write(artistDirectory.resolve("second.jpg"), new byte[]{3});

        assertThat(cache.getOrBuild(artist)).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("clip.mp4", "first.png");
        assertThat(cache.rebuild(artist)).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("second.jpg");
    }

    @Test
    void rebuildsUnreadableCache() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("broken-downloads");
        Path artistDirectory = downloadRoot.resolve("artist-two");
        Path cacheRoot = temporaryDirectory.resolve("broken-cache");
        Files.createDirectories(artistDirectory);
        Files.createDirectories(cacheRoot);
        Files.write(artistDirectory.resolve("valid.webp"), new byte[]{1});
        Files.write(cacheRoot.resolve("artist-7.json"), "not-json".getBytes(StandardCharsets.UTF_8));

        Artist artist = new Artist().setId(7).setName("artist-two").setArtistId("creator-two");
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot, cacheRoot, new ObjectMapper());

        assertThat(cache.getOrBuild(artist)).extracting(ArtistGalleryCache.CachedImage::getName)
                .containsExactly("valid.webp");
        assertThat(new String(Files.readAllBytes(cacheRoot.resolve("artist-7.json")), StandardCharsets.UTF_8))
                .startsWith("{\"v\":2");
    }
}

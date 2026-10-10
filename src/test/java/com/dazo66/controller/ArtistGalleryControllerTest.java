package com.dazo66.controller;

import com.dazo66.dashboard.ArtistGalleryData;
import com.dazo66.dashboard.ArtistGalleryItem;
import com.dazo66.service.ArtistGalleryCache;
import com.dazo66.service.FreeGalleryService;
import com.dazo66.util.ResultEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtistGalleryControllerTest {

    @org.junit.jupiter.api.BeforeEach
    void loginRoot() {com.dazo66.security.CurrentUser.set(new com.dazo66.security.CurrentUser("admin","admin",true,java.util.Arrays.asList("read:*","update:*","delete:*"),null));}
    @org.junit.jupiter.api.AfterEach
    void logoutRoot() {com.dazo66.security.CurrentUser.clear();}

    @TempDir
    Path temporaryDirectory;

    @Test
    void listsNewJpegFilesAndServesTheirThumbnailsAfterIndexWasCached() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("jpeg-downloads");
        Path directory = downloadRoot.resolve("artist");
        Files.createDirectories(directory);
        writeImage(directory.resolve("existing.png"));
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("jpeg-thumbnail-cache"));

        assertThat(controller.getGallery(directory.toString(), "name", "asc")
                .getBody().getData().getImages()).extracting(ArtistGalleryItem::getName)
                .containsExactly("existing.png");

        BufferedImage jpeg = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        assertThat(ImageIO.write(jpeg, "jpeg", directory.resolve("new.JPEG").toFile())).isTrue();
        assertThat(ImageIO.write(jpeg, "jpeg", directory.resolve("newer.jpeg").toFile())).isTrue();
        Files.setLastModifiedTime(directory, FileTime.fromMillis(
                Files.getLastModifiedTime(directory).toMillis() + 2000));

        List<ArtistGalleryItem> images = controller.getGallery(directory.toString(), "name", "asc")
                .getBody().getData().getImages();
        assertThat(images).extracting(ArtistGalleryItem::getName)
                .containsExactly("existing.png", "new.JPEG", "newer.jpeg");
        assertThat(images).extracting(ArtistGalleryItem::getType)
                .containsOnly("image");
        ResponseEntity<Resource> original = controller.getGalleryFile(directory.toString(), "new.JPEG", false);
        ResponseEntity<Resource> thumbnail = controller.getGalleryFile(directory.toString(), "new.JPEG", true);
        assertThat(original.getStatusCodeValue()).isEqualTo(200);
        assertThat(original.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_JPEG);
        assertThat(thumbnail.getStatusCodeValue()).isEqualTo(200);
        assertThat(thumbnail.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_JPEG);
        try (InputStream input = thumbnail.getBody().getInputStream()) {
            assertThat(ImageIO.read(input)).isNotNull();
        }
    }

    @Test
    void lazilyCachesThumbnailsWithoutLookingUpArtistForEveryImage() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("downloads");
        Path artistDirectory = downloadRoot.resolve("artist-one");
        Files.createDirectories(artistDirectory);
        Path source = artistDirectory.resolve("sample.png");
        writeImage(source);
        Files.write(artistDirectory.resolve("sample.mp4"), new byte[]{0, 0, 0, 24, 102, 116, 121, 112});
        Path nestedDirectory = artistDirectory.resolve("nested-album");
        Files.createDirectories(nestedDirectory);
        writeImage(nestedDirectory.resolve("nested.png"));

        Path cacheRoot = temporaryDirectory.resolve("cache").resolve("gallery-thumbnails");
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), cacheRoot);

        ResponseEntity<ResultEntity<ArtistGalleryData>> galleryResponse =
                controller.getGallery(artistDirectory.toString(), "created", "desc");
        ArtistGalleryData gallery = galleryResponse.getBody().getData();
        List<ArtistGalleryItem> images = gallery.getImages();

        assertThat(gallery.getFolderPath()).isEqualTo(artistDirectory.toRealPath().toString());
        assertThat(images).hasSize(3);
        ArtistGalleryItem image = images.stream().filter(item -> "image".equals(item.getType())).findFirst().get();
        ArtistGalleryItem video = images.stream().filter(item -> "video".equals(item.getType())).findFirst().get();
        ArtistGalleryItem folder = images.stream().filter(item -> "folder".equals(item.getType())).findFirst().get();
        assertThat(image.getName()).isEqualTo("sample.png");
        assertThat(video.getName()).isEqualTo("sample.mp4");
        com.fasterxml.jackson.databind.JsonNode json = new ObjectMapper().valueToTree(gallery);
        for (com.fasterxml.jackson.databind.JsonNode item : json.get("images")) {
            assertThat(item.has("url")).isFalse();
            assertThat(item.has("thumbnailUrl")).isFalse();
        }
        assertThat(folder.getName()).isEqualTo("nested-album");
        assertThat(folder.getFolderPath()).isEqualTo(nestedDirectory.toString());
        assertThat(controller.getGallery(folder.getFolderPath(), "name", "asc")
                .getBody().getData().getImages()).extracting(ArtistGalleryItem::getName)
                .containsExactly("nested.png");
        ResponseEntity<Resource> first = controller.getGalleryFile(artistDirectory.toString(), "sample.png", true);
        ResponseEntity<Resource> second = controller.getGalleryFile(artistDirectory.toString(), "sample.png", true);
        ResponseEntity<Resource> videoResponse = controller.getGalleryFile(artistDirectory.toString(), "sample.mp4", false);

        assertThat(first.getStatusCodeValue()).isEqualTo(200);
        assertThat(first.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(first.getHeaders().getCacheControl()).contains("private").contains("no-store");
        assertThat(first.getBody().getFile()).exists();
        assertThat(first.getBody().getFile().toPath()).startsWith(cacheRoot);
        assertThat(second.getBody().getFile()).isEqualTo(first.getBody().getFile());
        assertThat(videoResponse.getStatusCodeValue()).isEqualTo(200);
        assertThat(videoResponse.getHeaders().getContentType().toString()).isEqualTo("video/mp4");
        assertThat(videoResponse.getHeaders().getFirst("Accept-Ranges")).isEqualTo("bytes");
        assertThat(videoResponse.getBody().getFile().toPath()).isEqualTo(artistDirectory.resolve("sample.mp4"));
        try (java.util.stream.Stream<Path> cachedFiles = Files.walk(cacheRoot)) {
            assertThat(cachedFiles.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    @Test
    void generatesVideoFirstFrameOnlyOnThumbnailRequestAndReusesCache() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("video-preview-root");
        Path directory = downloadRoot.resolve("artist");
        Files.createDirectories(directory);
        Path video = directory.resolve("clip.mp4");
        Files.write(video, new byte[]{1, 2, 3});
        Path cacheRoot = temporaryDirectory.resolve("video-preview-cache");
        AtomicInteger extractions = new AtomicInteger();
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), cacheRoot) {
            @Override
            void extractVideoFrame(Path source, Path target) throws IOException {
                extractions.incrementAndGet();
                BufferedImage frame = new BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB);
                if (!ImageIO.write(frame, "jpg", target.toFile())) {
                    throw new IOException("JPEG encoder unavailable");
                }
            }
        };

        ResponseEntity<Resource> original = controller.getGalleryFile(directory.toString(), "clip.mp4", false);
        assertThat(original.getBody().getFile().toPath()).isEqualTo(video);
        assertThat(extractions.get()).isZero();

        ResponseEntity<Resource> first = controller.getGalleryFile(directory.toString(), "clip.mp4", true);
        ResponseEntity<Resource> second = controller.getGalleryFile(directory.toString(), "clip.mp4", true);
        assertThat(first.getStatusCodeValue()).isEqualTo(200);
        assertThat(first.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_JPEG);
        assertThat(first.getBody().getFile().toPath()).startsWith(cacheRoot);
        try (InputStream input = first.getBody().getInputStream()) {
            assertThat(ImageIO.read(input)).isNotNull();
        }
        assertThat(second.getBody().getFile()).isEqualTo(first.getBody().getFile());
        assertThat(extractions.get()).isEqualTo(1);
        Files.setLastModifiedTime(video, FileTime.fromMillis(
                Files.getLastModifiedTime(first.getBody().getFile().toPath()).toMillis() + 1000));
        ResponseEntity<Resource> refreshed = controller.getGalleryFile(directory.toString(), "clip.mp4", true);
        assertThat(refreshed.getStatusCodeValue()).isEqualTo(200);
        assertThat(extractions.get()).isEqualTo(2);
        try (java.util.stream.Stream<Path> sourceFiles = Files.list(directory)) {
            assertThat(sourceFiles.count()).isEqualTo(1);
        }
    }

    @Test
    void extractsRealVideoFrameWithoutExternalFfmpegExecutable() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("native-video-root");
        Path directory = downloadRoot.resolve("artist");
        Files.createDirectories(directory);
        Path video = directory.resolve("clip.mp4");
        BufferedImage frame = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, frame.getWidth(), frame.getHeight());
        } finally {
            graphics.dispose();
        }
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(video.toFile(), 64, 36);
             Java2DFrameConverter converter = new Java2DFrameConverter()) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
            recorder.setFrameRate(1);
            recorder.start();
            recorder.record(converter.convert(frame));
            recorder.stop();
        }
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("native-video-cache"));

        ResponseEntity<Resource> response = controller.getGalleryFile(directory.toString(), "clip.mp4", true);
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_JPEG);
        try (InputStream input = response.getBody().getInputStream()) {
            BufferedImage extracted = ImageIO.read(input);
            assertThat(extracted).isNotNull();
            assertThat(extracted.getWidth()).isLessThanOrEqualTo(600);
            assertThat(new Color(extracted.getRGB(extracted.getWidth() / 2, extracted.getHeight() / 2))
                    .getRed()).isGreaterThan(150);
        }
    }

    @Test
    void neverReturnsOriginalVideoWhenFrameExtractionFails() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("broken-video-root");
        Path directory = downloadRoot.resolve("artist");
        Files.createDirectories(directory);
        Files.write(directory.resolve("clip.mp4"), new byte[]{1, 2, 3});
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("broken-video-cache")) {
            @Override
            void extractVideoFrame(Path source, Path target) throws IOException {
                throw new IOException("Cannot decode video");
            }
        };

        assertThat(controller.getGalleryFile(directory.toString(), "clip.mp4", true)
                .getStatusCodeValue()).isEqualTo(500);
    }

    @Test
    void sortsAlbumsByLeadingDateAndKeepsFilesWithinEachDateAscending() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("album-downloads");
        Path artistDirectory = downloadRoot.resolve("artist-two");
        Files.createDirectories(artistDirectory);
        writeImage(artistDirectory.resolve("2026-07-23-set-02.png"));
        writeImage(artistDirectory.resolve("2026-07-23-set-01.png"));
        writeImage(artistDirectory.resolve("2025-12-01-set-01.png"));
        writeImage(artistDirectory.resolve("without-date.png"));
        Files.createDirectories(artistDirectory.resolve("nested"));

        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("album-cache"));

        List<ArtistGalleryItem> descending = controller.getGallery(artistDirectory.toString(), "album", "desc")
                .getBody().getData().getImages();
        List<ArtistGalleryItem> ascending = controller.getGallery(artistDirectory.toString(), "album", "asc")
                .getBody().getData().getImages();

        assertThat(descending).extracting(ArtistGalleryItem::getName).containsExactly(
                "nested",
                "2026-07-23-set-01.png",
                "2026-07-23-set-02.png",
                "2025-12-01-set-01.png",
                "without-date.png"
        );
        assertThat(ascending).extracting(ArtistGalleryItem::getName).containsExactly(
                "nested",
                "2025-12-01-set-01.png",
                "2026-07-23-set-01.png",
                "2026-07-23-set-02.png",
                "without-date.png"
        );
    }

    @Test
    void rejectsGalleryFoldersOutsideConfiguredDownloadRoot() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("safe-downloads");
        Path outsideDirectory = temporaryDirectory.resolve("outside");
        Files.createDirectories(downloadRoot);
        Files.createDirectories(outsideDirectory);
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("safe-cache"));

        assertThat(controller.getGallery(outsideDirectory.toString(), "name", "asc")
                .getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void acceptsExternalFolderWhenItBelongsToRegisteredFreeGallery() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("downloads-for-free-gallery");
        Path registeredDirectory = temporaryDirectory.resolve("registered-free-gallery");
        Path nestedDirectory = registeredDirectory.resolve("nested");
        Files.createDirectories(downloadRoot);
        Files.createDirectories(nestedDirectory);
        writeImage(nestedDirectory.resolve("free.png"));
        FreeGalleryService freeGalleryService = mock(FreeGalleryService.class);
        when(freeGalleryService.isRegisteredDirectory(nestedDirectory.toRealPath())).thenReturn(true);
        Path cacheRoot = temporaryDirectory.resolve("free-gallery-cache");
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot, cacheRoot, new ObjectMapper());
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), cacheRoot.resolve("thumbnails"), cache, freeGalleryService);

        ResponseEntity<ResultEntity<ArtistGalleryData>> response = controller.getGallery(
                nestedDirectory.toString(), "name", "asc");

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().getImages())
                .extracting(ArtistGalleryItem::getName).containsExactly("free.png");
    }

    @Test
    void servesFolderPosterThroughFileEndpointAsCachedTransparentPng() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("poster-root");
        Path folder = downloadRoot.resolve("posters");
        Files.createDirectories(folder);
        writeImage(folder.resolve("01 first.png"));
        writeImage(folder.resolve("02.png"));
        writeImage(folder.resolve("03.png"));
        Path cacheRoot = temporaryDirectory.resolve("poster-indexes");
        ArtistGalleryCache cache = new ArtistGalleryCache(downloadRoot, cacheRoot, new ObjectMapper());
        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("poster-thumbnails"), cache);

        assertThat(cache.posterFile(folder.toString())).doesNotExist();
        assertThat(controller.getGalleryFile(downloadRoot.toString(), "posters", false)
                .getStatusCodeValue()).isEqualTo(404);
        assertThat(cache.posterFile(folder.toString())).doesNotExist();

        cache.rebuildScheduledTree(downloadRoot.toString());

        ArtistGalleryItem item = controller.getGallery(downloadRoot.toString(), "name", "asc")
                .getBody().getData().getImages().get(0);

        assertThat(item.getPosterUrl()).startsWith("/api/fanbox/gallery/file?folderPath=");
        assertThat(item.getPosterUrl()).contains("&path=posters").doesNotContain("thumbnail=true", "01%20first.png");
        ResponseEntity<Resource> posterResponse = controller.getGalleryFile(downloadRoot.toString(), "posters", false);
        assertThat(posterResponse.getStatusCodeValue()).isEqualTo(200);
        assertThat(posterResponse.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_PNG);
        BufferedImage posterImage = ImageIO.read(posterResponse.getBody().getInputStream());
        assertThat(posterImage.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(posterImage.getRGB(0, 0), true).getAlpha()).isZero();
        assertThat(controller.getGalleryFile(downloadRoot.toString(), "posters", true)
                .getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.IMAGE_PNG);
        assertThat(controller.getGalleryFile(temporaryDirectory.toString(), "posters", false)
                .getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void servesVideoByteRangesForSeeking() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("video-downloads");
        Path artistDirectory = downloadRoot.resolve("video-artist");
        Files.createDirectories(artistDirectory);
        byte[] videoBytes = new byte[]{0, 0, 0, 24, 102, 116, 121, 112};
        Files.write(artistDirectory.resolve("clip.mp4"), videoBytes);

        ArtistGalleryController controller = new ArtistGalleryController(
                downloadRoot.toString(), temporaryDirectory.resolve("video-cache"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/fanbox/gallery/file")
                        .param("folderPath", artistDirectory.toString())
                        .param("path", "clip.mp4")
                        .header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/8"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(new byte[]{0, 24, 102, 116}));
    }

    @Test
    void fileDetailsHeadIncludesActualModificationTime() throws Exception {
        Path directory=Files.createDirectories(temporaryDirectory.resolve("metadata"));
        Path file=directory.resolve("photo.png");writeImage(file);
        long timestamp=1600000000000L;Files.setLastModifiedTime(file,FileTime.fromMillis(timestamp));
        ArtistGalleryController controller=new ArtistGalleryController(directory.toString(),temporaryDirectory.resolve("metadata-thumbs"));
        MockMvcBuilders.standaloneSetup(controller).build()
            .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/fanbox/gallery/file").param("folderPath",directory.toString()).param("path","photo.png"))
            .andExpect(status().isOk())
            .andExpect(header().dateValue("Last-Modified",timestamp))
            .andExpect(header().string("Content-Length",String.valueOf(Files.size(file))));
    }

    @Test
    void randomOrderShufflesEveryQueryWithoutChangingCachedItems() throws Exception {
        Path directory=Files.createDirectories(temporaryDirectory.resolve("random"));
        for(int i=0;i<8;i++)writeImage(directory.resolve("image-"+i+".png"));
        ArtistGalleryController controller=new ArtistGalleryController(directory.toString(),temporaryDirectory.resolve("random-thumbs"));
        java.util.List<String> sorted=controller.getGallery(directory.toString(),"name","asc").getBody().getData().getImages().stream().map(ArtistGalleryItem::getName).collect(java.util.stream.Collectors.toList());
        java.util.Set<java.util.List<String>> orders=new java.util.HashSet<>();
        for(int i=0;i<10;i++) {
            java.util.List<String> names=controller.getGallery(directory.toString(),"random",i%2==0?"asc":"desc").getBody().getData().getImages().stream().map(ArtistGalleryItem::getName).collect(java.util.stream.Collectors.toList());
            assertThat(names).containsExactlyInAnyOrderElementsOf(sorted);orders.add(names);
        }
        assertThat(orders.size()).isGreaterThan(1);
        assertThat(controller.getGallery(directory.toString(),"name","asc").getBody().getData().getImages()).extracting(ArtistGalleryItem::getName).containsExactlyElementsOf(sorted);
    }

    @Test
    void listsMissingCachedEntriesWithoutCheckingTheirExistence() throws Exception {
        Path root = temporaryDirectory.resolve("stale-gallery");
        Files.createDirectories(root);
        ArtistGalleryCache cache = mock(ArtistGalleryCache.class);
        when(cache.getOrBuildAuthorized(root.toRealPath().toString())).thenReturn(java.util.Arrays.asList(
                new ArtistGalleryCache.CachedImage("missing.png", "image", 1L),
                new ArtistGalleryCache.CachedImage("missing.mp4", "video", 2L),
                new ArtistGalleryCache.CachedImage("missing-album", "folder", 3L)));
        ArtistGalleryController controller = new ArtistGalleryController(
                root.toString(), temporaryDirectory.resolve("stale-thumbnails"), cache);
        assertThat(controller.getGallery(root.toString(), "name", "asc").getBody().getData().getImages())
                .extracting(ArtistGalleryItem::getName)
                .containsExactly("missing-album", "missing.mp4", "missing.png");
        com.dazo66.security.CurrentUser.set(new com.dazo66.security.CurrentUser(
                "reader", "reader", false, java.util.Arrays.asList(
                "read:" + root, "read:" + root.resolve("missing.png")), null));
        assertThat(controller.getGallery(root.toString(), "name", "asc").getBody().getData().getImages())
                .extracting(ArtistGalleryItem::getName).containsExactly("missing.png");
        assertThat(controller.getGalleryFile(root.toString(), "missing.png", false).getStatusCodeValue())
                .isEqualTo(404);
    }

    private void writeImage(Path path) throws Exception {
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(33, 142, 197));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", path.toFile());
    }

    @Test
    void filtersRestrictedFilesAndRejectsDirectThumbnailAndVideoAccess() throws Exception {
        Path root=temporaryDirectory.resolve("restricted");Files.createDirectories(root);
        writeImage(root.resolve("allowed.png"));writeImage(root.resolve("secret.png"));
        Files.write(root.resolve("secret.mp4"),new byte[]{1,2,3});
        com.dazo66.security.CurrentUser.set(new com.dazo66.security.CurrentUser("reader","reader",false,java.util.Arrays.asList("read:"+root,"read:"+root.resolve("allowed.png")),null));
        ArtistGalleryController controller=new ArtistGalleryController(root.toString(),temporaryDirectory.resolve("restricted-cache"));
        assertThat(controller.getGallery(root.toString(),"name","asc").getBody().getData().getImages()).extracting(ArtistGalleryItem::getName).containsExactly("allowed.png");
        assertThat(controller.getGalleryFile(root.toString(),"allowed.png",true).getStatusCodeValue()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->controller.getGalleryFile(root.toString(),"secret.png",true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->controller.getGalleryFile(root.toString(),"secret.mp4",false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

}

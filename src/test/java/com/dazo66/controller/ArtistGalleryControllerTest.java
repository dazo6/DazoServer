package com.dazo66.controller;

import com.dazo66.dashboard.ArtistGalleryData;
import com.dazo66.dashboard.ArtistGalleryItem;
import com.dazo66.dashboard.ArtistManagementItem;
import com.dazo66.entity.Artist;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.ResultEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtistGalleryControllerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void lazilyCachesThumbnailsWithoutLookingUpArtistForEveryImage() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("downloads");
        Path artistDirectory = downloadRoot.resolve("artist-one");
        Files.createDirectories(artistDirectory);
        Path source = artistDirectory.resolve("sample.png");
        writeImage(source);
        Files.write(artistDirectory.resolve("sample.mp4"), new byte[]{0, 0, 0, 24, 102, 116, 121, 112});

        Artist artist = new Artist().setId(7).setName("artist-one").setArtistId("creator-one")
                .setType("fanbox");
        ArtistManagementItem artistItem = new ArtistManagementItem();
        artistItem.setId(7);
        artistItem.setName("artist-one");
        KemonoArtistService artistService = mock(KemonoArtistService.class);
        ArtistManagementService managementService = mock(ArtistManagementService.class);
        when(artistService.getById(7)).thenReturn(artist);
        when(managementService.toItem(artist)).thenReturn(artistItem);
        Path cacheRoot = temporaryDirectory.resolve("cache").resolve("gallery-thumbnails");
        ArtistGalleryController controller = new ArtistGalleryController(
                artistService, managementService, downloadRoot.toString(), cacheRoot);

        ResponseEntity<ResultEntity<ArtistGalleryData>> galleryResponse =
                controller.getGallery(7, "created", "desc");
        ArtistGalleryData gallery = galleryResponse.getBody().getData();
        List<ArtistGalleryItem> images = gallery.getImages();

        assertThat(gallery.getArtist().getArtistDirectory()).isEqualTo("artist-one");
        assertThat(images).hasSize(2);
        ArtistGalleryItem image = images.stream().filter(item -> "image".equals(item.getType())).findFirst().get();
        ArtistGalleryItem video = images.stream().filter(item -> "video".equals(item.getType())).findFirst().get();
        assertThat(image.getThumbnailUrl()).endsWith("&thumbnail=true");
        assertThat(video.getThumbnailUrl()).isNull();
        assertThat(video.getUrl()).contains("sample.mp4");
        verify(artistService, times(1)).getById(7);

        ResponseEntity<Resource> first = controller.getGalleryFile("artist-one", "sample.png", true);
        ResponseEntity<Resource> second = controller.getGalleryFile("artist-one", "sample.png", true);
        ResponseEntity<Resource> videoResponse = controller.getGalleryFile("artist-one", "sample.mp4", false);

        assertThat(first.getStatusCodeValue()).isEqualTo(200);
        assertThat(first.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(first.getHeaders().getCacheControl()).contains("private").contains("max-age=600");
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
        verify(artistService, times(1)).getById(7);
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

        Artist artist = new Artist().setId(8).setName("artist-two").setArtistId("creator-two")
                .setType("fanbox");
        ArtistManagementItem artistItem = new ArtistManagementItem();
        KemonoArtistService artistService = mock(KemonoArtistService.class);
        ArtistManagementService managementService = mock(ArtistManagementService.class);
        when(artistService.getById(8)).thenReturn(artist);
        when(managementService.toItem(artist)).thenReturn(artistItem);
        ArtistGalleryController controller = new ArtistGalleryController(
                artistService, managementService, downloadRoot.toString(), temporaryDirectory.resolve("album-cache"));

        List<ArtistGalleryItem> descending = controller.getGallery(8, "album", "desc")
                .getBody().getData().getImages();
        List<ArtistGalleryItem> ascending = controller.getGallery(8, "album", "asc")
                .getBody().getData().getImages();

        assertThat(descending).extracting(ArtistGalleryItem::getName).containsExactly(
                "2026-07-23-set-01.png",
                "2026-07-23-set-02.png",
                "2025-12-01-set-01.png",
                "without-date.png"
        );
        assertThat(ascending).extracting(ArtistGalleryItem::getName).containsExactly(
                "2025-12-01-set-01.png",
                "2026-07-23-set-01.png",
                "2026-07-23-set-02.png",
                "without-date.png"
        );
    }

    @Test
    void servesVideoByteRangesForSeeking() throws Exception {
        Path downloadRoot = temporaryDirectory.resolve("video-downloads");
        Path artistDirectory = downloadRoot.resolve("video-artist");
        Files.createDirectories(artistDirectory);
        byte[] videoBytes = new byte[]{0, 0, 0, 24, 102, 116, 121, 112};
        Files.write(artistDirectory.resolve("clip.mp4"), videoBytes);

        ArtistGalleryController controller = new ArtistGalleryController(
                mock(KemonoArtistService.class), mock(ArtistManagementService.class),
                downloadRoot.toString(), temporaryDirectory.resolve("video-cache"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/fanbox/gallery/file")
                        .param("artistDirectory", "video-artist")
                        .param("path", "clip.mp4")
                        .header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/8"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(new byte[]{0, 24, 102, 116}));
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
}

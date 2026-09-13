package com.dazo66.controller;

import com.dazo66.service.ArtistImageCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistImageControllerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void servesOnlyExistingCacheFiles() throws Exception {
        String fileName = "artist-7-banner-0123456789abcdef0123456789abcdef.webp";
        Files.write(temporaryDirectory.resolve(fileName), new byte[]{1, 2, 3});
        ArtistImageController controller = new ArtistImageController(
                new ArtistImageCache(temporaryDirectory));

        ResponseEntity<Resource> found = controller.getArtistImage(fileName);
        ResponseEntity<Resource> traversal = controller.getArtistImage("../application.properties");

        assertThat(found.getStatusCodeValue()).isEqualTo(200);
        assertThat(found.getBody()).isNotNull();
        assertThat(found.getHeaders().getCacheControl()).contains("max-age=31536000");
        assertThat(traversal.getStatusCodeValue()).isEqualTo(404);
    }
}

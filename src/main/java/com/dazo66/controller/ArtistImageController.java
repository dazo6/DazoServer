package com.dazo66.controller;

import com.dazo66.service.ArtistImageCache;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Serves content-addressed artist images from the local cache.
 */
@RestController
@RequestMapping("api/fanbox/artist-image")
public class ArtistImageController {

    private final ArtistImageCache imageCache;

    public ArtistImageController(ArtistImageCache imageCache) {
        this.imageCache = imageCache;
    }

    @GetMapping("{fileName:.+}")
    public ResponseEntity<Resource> getArtistImage(@PathVariable String fileName) {
        Path file = imageCache.resolve(fileName);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = new FileSystemResource(file);
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate());
        try {
            response.contentLength(resource.contentLength());
        } catch (IOException ignored) {
            // The file can still be streamed if its length cannot be queried.
        }
        return response.body(resource);
    }
}

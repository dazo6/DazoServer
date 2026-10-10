package com.dazo66.controller;

import com.dazo66.security.*;
import com.dazo66.service.ArtistImageCache;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.entity.Artist;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired private KemonoArtistService artists;
    @Autowired private ArtistManagementService management;

    public ArtistImageController(ArtistImageCache imageCache) {
        this.imageCache = imageCache;
    }

    @GetMapping("{fileName:.+}")
    public ResponseEntity<Resource> getArtistImage(@PathVariable String fileName) {
        Path file = imageCache.resolve(fileName);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        CurrentUser u=CurrentUser.require();
        if(!u.superAdmin && !AclAccess.can(AclType.READ,"*")) {
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("^artist-(\\d+)-.*").matcher(fileName);
            if(!match.matches()) return ResponseEntity.notFound().build();
            Artist artist=artists.getById(Integer.valueOf(match.group(1)));
            if(artist==null || !AclAccess.canPath(AclType.READ,management.resolveFolderPath(artist))) return ResponseEntity.status(403).build();
        }
        FileSystemResource resource = new FileSystemResource(file);
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore().cachePrivate());
        try {
            response.contentLength(resource.contentLength());
        } catch (IOException ignored) {
            // The file can still be streamed if its length cannot be queried.
        }
        return response.body(resource);
    }
}

package com.dazo66.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual filesystem check. Set gallery.cover.folderPath to run it; normal test runs skip it.
 * This refreshes the folder's parent index using the same scheduled-index path as production.
 */
class ArtistGalleryManualCoverTest {

    @Test
    void rebuildsAndChecksFolderCoverInGalleryIndex() throws Exception {

        Path folder = Paths.get("D:\\DazoServer\\test\\fanbox\\こもれび").toAbsolutePath().normalize();
        assertThat(folder).isDirectory();
        folder = folder.toRealPath();
        Path parent = folder.getParent();
        assertThat(parent).isNotNull();

        Path cacheRoot = Paths.get(System.getProperty("gallery.cover.cachePath", "cache/gallery-indexes"));
        ArtistGalleryCache cache = new ArtistGalleryCache(parent, cacheRoot, new ObjectMapper());
        cache.rebuildScheduledTree(parent.toString());

        List<ArtistGalleryCache.CachedImage> items = cache.getOrBuild(parent.toString());
        Path selectedFolder = folder;
        ArtistGalleryCache.CachedImage entry = items.stream()
                .filter(item -> "folder".equals(item.getType()))
                .filter(item -> item.getName().equals(selectedFolder.getFileName().toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Parent gallery index does not contain the folder"));

        assertThat(entry.getPosterName()).isEqualTo(cache.posterFile(folder.toString()).getFileName().toString());
        assertThat(cache.posterFile(folder.toString())).isRegularFile();
        System.out.println("Folder cover index: " + folder);
        System.out.println("Cached poster: " + cache.posterFile(folder.toString()));
        System.out.println("Cache directory: " + cacheRoot.toAbsolutePath().normalize());
    }
}

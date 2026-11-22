package com.dazo66.controller;

import com.dazo66.security.CurrentUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GalleryBatchDeleteTest {
    @TempDir Path root;
    @BeforeEach void login() {CurrentUser.set(new CurrentUser("admin","admin",true,Arrays.asList("read:*","delete:*"),null));}
    @AfterEach void logout() {CurrentUser.clear();}
    ArtistGalleryController controller() {return new ArtistGalleryController(root.toString(),root.resolveSibling(root.getFileName()+"-thumbs"));}
    @Test void deletesFilesAndNestedFoldersAndRefreshesIndex() throws Exception {
        Path gallery=Files.createDirectories(root.resolve("gallery"));
        Files.write(gallery.resolve("a.jpg"),new byte[]{1});
        Path nested=Files.createDirectories(gallery.resolve("album/deep"));
        Files.write(nested.resolve("anything.txt"),new byte[]{2});
        Files.write(gallery.resolve("keep.jpg"),new byte[]{3});
        ArtistGalleryController c=controller();
        c.getGallery(gallery.toString(),"name","asc");
        Map<String,Object> result=c.deleteGalleryFiles(gallery.toString(),Arrays.asList("a.jpg","album","a.jpg")).getData();
        assertThat((List<?>)result.get("deleted")).hasSize(2);
        assertThat((Map<?,?>)result.get("failed")).isEmpty();
        assertThat(gallery.resolve("a.jpg")).doesNotExist();assertThat(gallery.resolve("album")).doesNotExist();
        assertThat(gallery.resolve("keep.jpg")).exists();
        assertThat(c.getGallery(gallery.toString(),"name","asc").getBody().getData().getImages()).extracting("name").containsExactly("keep.jpg");
    }
    @Test void validatesWholeBatchBeforeDeletingAndRejectsTraversal() throws Exception {
        Files.write(root.resolve("safe.jpg"),new byte[]{1});
        for(String invalid:Arrays.asList("..",".","../outside","a/b","a\\b","C:abc","safe.jpg.")) {
            assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Arrays.asList("safe.jpg",invalid))).isInstanceOf(IllegalArgumentException.class);
            assertThat(root.resolve("safe.jpg")).exists();
        }
        assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Arrays.asList("safe.jpg","missing"))).isInstanceOf(java.io.IOException.class);
        assertThat(root.resolve("safe.jpg")).exists();
    }
    @Test void childPermissionsCannotBeBypassedByDeletingParent() throws Exception {
        Path album=Files.createDirectories(root.resolve("album"));Files.write(album.resolve("protected.txt"),new byte[]{1});
        Files.write(root.resolve("safe.jpg"),new byte[]{1});
        CurrentUser.set(new CurrentUser("limited","limited",false,Arrays.asList("read:*","delete:"+root,"delete:"+album,"delete:"+root.resolve("safe.jpg")),null));
        assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Arrays.asList("safe.jpg","album"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(root.resolve("safe.jpg")).exists();assertThat(album.resolve("protected.txt")).exists();
    }
    @Test void requiresLoginAndRejectsEmptyOrOversizedSelection() throws Exception {
        assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Collections.emptyList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Collections.nCopies(1001,"x"))).isInstanceOf(IllegalArgumentException.class);
        CurrentUser.clear();
        assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Arrays.asList("x"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void neverFollowsLinksOutsideGallery() throws Exception {
        Path outside=Files.createTempDirectory("gallery-delete-outside-");
        Path keep=Files.write(outside.resolve("keep.txt"),new byte[]{1});
        Path link=root.resolve("album/link");
        try {
            Files.write(root.resolve("safe.jpg"),new byte[]{1});
            Path album=Files.createDirectories(root.resolve("album"));
            try {Files.createSymbolicLink(link,outside);}
            catch(java.io.IOException | UnsupportedOperationException e) {
                Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"),"Symbolic links unavailable");
                Process junction=new ProcessBuilder("cmd.exe","/c","mklink","/J",link.toAbsolutePath().toString(),outside.toAbsolutePath().toString()).redirectErrorStream(true).start();
                while(junction.getInputStream().read()!=-1) {}
                Assumptions.assumeTrue(junction.waitFor()==0,"Directory junctions unavailable");
            }
            assertThatThrownBy(()->controller().deleteGalleryFiles(root.toString(),Arrays.asList("safe.jpg","album"))).isInstanceOf(IllegalArgumentException.class);
            assertThat(keep).exists();assertThat(root.resolve("safe.jpg")).exists();
        } finally {Files.deleteIfExists(link);Files.deleteIfExists(keep);Files.deleteIfExists(outside);}
    }
}

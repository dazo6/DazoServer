package com.dazo66.controller;

import com.dazo66.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArtistGalleryArchiveControllerTest {
    @TempDir Path temporary;
    @AfterEach void logout(){CurrentUser.clear();}
    void reader(String permission){CurrentUser.set(new CurrentUser("reader","reader",false,Collections.singletonList(permission),null));}
    @Test void readOnlyUserCanPrepareAndDownloadButRevokedPermissionDeniesCacheHit() throws Exception {
        Path root=Files.createDirectories(temporary.resolve("root"));
        Path folder=Files.createDirectories(root.resolve("图集"));
        Files.write(folder.resolve("file.txt"),new byte[]{7,8});
        ArtistGalleryController controller=new ArtistGalleryController(root.toString(),temporary.resolve("thumbnails"));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new AclExceptionHandler()).build();
        reader("read:*");
        mvc.perform(get("/api/fanbox/gallery/archive").param("folderPath",folder.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.filename").value("图集.zip"))
            .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/api/fanbox/gallery/archive/download?folderPath=")));
        mvc.perform(get("/api/fanbox/gallery/archive/download").param("folderPath",folder.toString()))
            .andExpect(status().isOk()).andExpect(content().contentType("application/zip"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment")));
        reader("read:"+GlobPermissions.canonical(folder.toString())+"*");
        mvc.perform(get("/api/fanbox/gallery/archive/download").param("folderPath",folder.toString())).andExpect(status().isOk());
        reader("read:"+GlobPermissions.canonical(folder.toString()));
        mvc.perform(get("/api/fanbox/gallery/archive").param("folderPath",folder.toString())).andExpect(status().isForbidden());
        mvc.perform(get("/api/fanbox/gallery/archive/download").param("folderPath",folder.toString())).andExpect(status().isForbidden());
        CurrentUser.clear();
        mvc.perform(get("/api/fanbox/gallery/archive/download").param("folderPath",folder.toString())).andExpect(status().isUnauthorized());
    }
    @Test void rejectsUnregisteredExternalFolderAndMissingArchives() throws Exception {
        Path root=Files.createDirectories(temporary.resolve("root")),external=Files.createDirectories(temporary.resolve("external"));
        ArtistGalleryController controller=new ArtistGalleryController(root.toString(),temporary.resolve("thumbnails"));
        reader("read:*");
        assertThat(controller.downloadArchive(root.toString()).getStatusCodeValue()).isEqualTo(404);
        assertThatThrownBy(()->controller.prepareArchive(external.toString())).hasMessageContaining("404");
    }
}

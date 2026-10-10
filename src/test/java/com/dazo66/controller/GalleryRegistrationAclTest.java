package com.dazo66.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.dashboard.ArtistManagementItem;
import com.dazo66.entity.Artist;
import com.dazo66.entity.FreeGallery;
import com.dazo66.security.*;
import com.dazo66.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GalleryRegistrationAclTest {
    @TempDir Path root;
    @AfterEach void clear() { CurrentUser.clear(); }

    @Test void restrictedUserSeesArtistPageWithoutPermissionFiltering() {
        CurrentUser.set(new CurrentUser("zlzz","zlzz",false,Collections.singletonList("read:"+root),null));
        Artist a=new Artist().setId(1).setArtistId("1").setName("secret").setDomain(com.dazo66.entity.ArtistDomainEnum.FANBOX);
        KemonoArtistService source=mock(KemonoArtistService.class);
        Page<Artist> page=new Page<>(2,20,45);
        page.setRecords(Collections.singletonList(a));
        when(source.getArtistByPage(eq(2),eq(20),any())).thenReturn(page);
        ArtistManagementService presentation=new ArtistManagementService();
        ReflectionTestUtils.setField(presentation,"downloadPath",root.toString());
        FanboxController controller=new FanboxController();
        ReflectionTestUtils.setField(controller,"kemonoArtistService",source);
        ReflectionTestUtils.setField(controller,"artistManagementService",presentation);
        Page<ArtistManagementItem> result=controller.getFanboxArtist(2,20,null,null,"desc").getData();
        assertThat(result.getTotal()).isEqualTo(45);
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(ArtistManagementItem::getName).containsExactly("secret");
        assertThat(result.getRecords().get(0).getCapabilities()).containsEntry("canOpenGallery",false);
        verify(source,times(1)).getArtistByPage(eq(2),eq(20),any());
    }

    @Test void registeredFreeGalleryRemainsVisibleButOpeningItsContentIsForbidden() throws Exception {
        Path directory=Files.createDirectories(root.resolve("secret"));
        CurrentUser.set(new CurrentUser("zlzz","zlzz",false,Collections.singletonList("read:"+root),null));
        FreeGallery gallery=new FreeGallery().setId(1).setName("secret").setFolderPath(directory.toString());
        FreeGalleryService source=mock(FreeGalleryService.class);
        when(source.list("secret")).thenReturn(Collections.singletonList(gallery));
        assertThat(new FreeGalleryController(source).list("secret").getData()).containsExactly(gallery);
        ArtistGalleryController content=new ArtistGalleryController(root.toString(),root.resolve("cache"));
        assertThatThrownBy(()->content.getGallery(directory.toString(),"name","asc"))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(403));
    }
}

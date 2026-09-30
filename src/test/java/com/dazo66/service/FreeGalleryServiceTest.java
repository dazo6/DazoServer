package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dazo66.dashboard.FreeGalleryRequest;
import com.dazo66.entity.FreeGallery;
import com.dazo66.mapper.FreeGalleryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreeGalleryServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void registersRealDirectoryAndUsesFolderNameWhenNameIsBlank() throws Exception {
        Path folder = temporaryDirectory.resolve("My Gallery");
        Files.createDirectories(folder);
        FreeGalleryMapper mapper = mock(FreeGalleryMapper.class);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        FreeGalleryService service = new FreeGalleryService(mapper);
        FreeGalleryRequest request = new FreeGalleryRequest();
        request.setFolderPath(folder.toString());
        request.setName("  ");

        FreeGallery gallery = service.create(request);

        assertThat(gallery.getFolderPath()).isEqualTo(folder.toRealPath().toString());
        assertThat(gallery.getName()).isEqualTo("My Gallery");
        assertThat(gallery.getGmtCreate()).isNotNull();
        assertThat(gallery.getGmtModified()).isEqualTo(gallery.getGmtCreate());
    }

    @Test
    void rejectsMissingAndDuplicateDirectories() throws Exception {
        FreeGalleryMapper mapper = mock(FreeGalleryMapper.class);
        FreeGalleryService service = new FreeGalleryService(mapper);
        FreeGalleryRequest missing = new FreeGalleryRequest();
        missing.setFolderPath(temporaryDirectory.resolve("missing").toString());

        assertThatThrownBy(() -> service.create(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");

        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        FreeGalleryRequest duplicate = new FreeGalleryRequest();
        duplicate.setFolderPath(temporaryDirectory.toString());
        assertThatThrownBy(() -> service.create(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经注册");
    }

    @Test
    void authorizesRegisteredFolderAndItsChildrenOnly() throws Exception {
        Path registered = temporaryDirectory.resolve("registered");
        Path child = registered.resolve("nested");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(child);
        Files.createDirectories(outside);
        FreeGalleryMapper mapper = mock(FreeGalleryMapper.class);
        when(mapper.selectList(null)).thenReturn(Collections.singletonList(
                new FreeGallery().setFolderPath(registered.toRealPath().toString())));
        FreeGalleryService service = new FreeGalleryService(mapper);

        assertThat(service.isRegisteredDirectory(registered.toRealPath())).isTrue();
        assertThat(service.isRegisteredDirectory(child.toRealPath())).isTrue();
        assertThat(service.isRegisteredDirectory(outside.toRealPath())).isFalse();
    }
}

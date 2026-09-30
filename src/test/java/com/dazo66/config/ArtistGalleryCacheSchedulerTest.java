package com.dazo66.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.FreeGallery;
import com.dazo66.service.ArtistGalleryCache;
import com.dazo66.service.FreeGalleryService;
import com.dazo66.service.KemonoArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ArtistGalleryCacheSchedulerTest {

    @Test
    void rebuildsEveryArtistAcrossPages() throws Exception {
        Artist first = new Artist().setId(1).setName("first");
        Artist second = new Artist().setId(2).setName("second");
        Page<Artist> firstPage = new Page<>(1, 500, 501);
        firstPage.setRecords(Collections.singletonList(first));
        Page<Artist> secondPage = new Page<>(2, 500, 501);
        secondPage.setRecords(Collections.singletonList(second));

        KemonoArtistService artistService = mock(KemonoArtistService.class);
        ArtistGalleryCache galleryCache = mock(ArtistGalleryCache.class);
        FreeGalleryService freeGalleryService = mock(FreeGalleryService.class);
        when(artistService.getArtistByPage(eq(1), eq(500), any(Wrapper.class))).thenReturn(firstPage);
        when(artistService.getArtistByPage(eq(2), eq(500), any(Wrapper.class))).thenReturn(secondPage);
        when(freeGalleryService.list(null)).thenReturn(Collections.singletonList(
                new FreeGallery().setFolderPath("C:/galleries/free")));

        new ArtistGalleryCacheScheduler(artistService, galleryCache, freeGalleryService).rebuildAll();

        verify(galleryCache).rebuildScheduledTree(first);
        verify(galleryCache).rebuildScheduledTree(second);
        verify(galleryCache).rebuildScheduledTree("C:/galleries/free");
    }

    @Test
    void runsEveryDayAtThirteenFifteenInShanghai() throws Exception {
        Method method = ArtistGalleryCacheScheduler.class.getMethod("rebuildAll");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 15 13 * * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}

package com.dazo66.config;

import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.service.ArtistImageCache;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.service.KemonoArtistService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.MediaType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class ArtistIconSchedulerTest {

    @Test
    void downloadsKemonoImagesAndStoresOnlyCacheFileNames() throws Exception {
        KemonoProperties properties = new KemonoProperties();
        properties.setMode(KemonoProperties.Mode.JSON);
        properties.setBaseUrl("https://pawchive.pw/");
        ArtistManagementService managementService = mock(ArtistManagementService.class);
        when(managementService.effectiveDomain(org.mockito.ArgumentMatchers.any(Artist.class)))
                .thenReturn(ArtistDomainEnum.KEMONO);
        Artist artist = new Artist().setId(7).setType("patreon").setArtistId("84167799");
        Page<Artist> page = new Page<>();
        page.setRecords(Collections.singletonList(artist));
        KemonoArtistService artistService = mock(KemonoArtistService.class);
        when(artistService.getArtistByPage(anyInt(), anyInt(), any(Wrapper.class))).thenReturn(page);
        OkHttpClient client = mock(OkHttpClient.class);
        ArtistImageCache imageCache = mock(ArtistImageCache.class);
        String iconFile = "artist-7-icon-0123456789abcdef0123456789abcdef.jpg";
        String bannerFile = "artist-7-banner-fedcba9876543210fedcba9876543210.webp";
        when(imageCache.download(eq(client), any(com.squareup.okhttp.Request.class), eq(7), eq("icon")))
                .thenReturn(iconFile);
        when(imageCache.download(eq(client), any(com.squareup.okhttp.Request.class), eq(7), eq("banner")))
                .thenReturn(bannerFile);
        ArtistIconScheduler scheduler = new ArtistIconScheduler(
                artistService, managementService, properties, mock(CookieConfig.class), client, imageCache);

        scheduler.refreshIcons();

        verify(imageCache).download(eq(client), org.mockito.ArgumentMatchers.argThat(request ->
                "https://pawchive.pw/icons/patreon/84167799".equals(request.urlString())),
                eq(7), eq("icon"));
        verify(imageCache).download(eq(client), org.mockito.ArgumentMatchers.argThat(request ->
                "https://pawchive.pw/banners/patreon/84167799".equals(request.urlString())),
                eq(7), eq("banner"));
        verify(artistService).updateById(org.mockito.ArgumentMatchers.argThat(updated ->
                iconFile.equals(updated.getIcon()) && bannerFile.equals(updated.getBanner())));
    }

    @Test
    void refreshesFanboxIconAndBannerFromOneCreatorResponse() throws Exception {
        KemonoProperties properties = new KemonoProperties();
        ArtistManagementService managementService = mock(ArtistManagementService.class);
        when(managementService.effectiveDomain(any(Artist.class))).thenReturn(ArtistDomainEnum.FANBOX);

        Artist artist = new Artist().setId(7).setArtistId("bttn").setName("bttn");
        Page<Artist> page = new Page<>();
        page.setRecords(Collections.singletonList(artist));
        KemonoArtistService artistService = mock(KemonoArtistService.class);
        when(artistService.getArtistByPage(anyInt(), anyInt(), any(Wrapper.class))).thenReturn(page);

        Call call = mock(Call.class);
        String json = "{\"body\":{\"user\":{\"iconUrl\":\"https://img/icon.jpg\"},"
                + "\"coverImageUrl\":\"https://img/banner.jpg\"}}";
        com.squareup.okhttp.Response response = new com.squareup.okhttp.Response.Builder()
                .request(new com.squareup.okhttp.Request.Builder()
                        .url("https://api.fanbox.cc/creator.get?creatorId=bttn").build())
                .protocol(com.squareup.okhttp.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(com.squareup.okhttp.ResponseBody.create(
                        MediaType.parse("application/json"), json))
                .build();
        when(call.execute()).thenReturn(response);
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(com.squareup.okhttp.Request.class))).thenReturn(call);
        ArtistImageCache imageCache = mock(ArtistImageCache.class);
        String iconFile = "artist-7-icon-0123456789abcdef0123456789abcdef.jpg";
        String bannerFile = "artist-7-banner-fedcba9876543210fedcba9876543210.jpg";
        when(imageCache.download(eq(client), any(com.squareup.okhttp.Request.class), eq(7), eq("icon")))
                .thenReturn(iconFile);
        when(imageCache.download(eq(client), any(com.squareup.okhttp.Request.class), eq(7), eq("banner")))
                .thenReturn(bannerFile);

        ArtistIconScheduler scheduler = new ArtistIconScheduler(
                artistService, managementService, properties, mock(CookieConfig.class), client, imageCache);
        scheduler.refreshIcons();

        verify(client, times(1)).newCall(any(com.squareup.okhttp.Request.class));
        verify(artistService).updateById(org.mockito.ArgumentMatchers.argThat(updated ->
                iconFile.equals(updated.getIcon()) && bannerFile.equals(updated.getBanner())));
    }
}

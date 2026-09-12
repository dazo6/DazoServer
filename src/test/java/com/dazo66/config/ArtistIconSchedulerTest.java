package com.dazo66.config;

import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.service.KemonoArtistService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.MediaType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

class ArtistIconSchedulerTest {

    @Test
    void buildsKemonoImagesWithConfiguredHostEvenInJsonMode() {
        KemonoProperties properties = new KemonoProperties();
        properties.setMode(KemonoProperties.Mode.JSON);
        properties.setBaseUrl("https://pawchive.pw/");
        ArtistManagementService managementService = mock(ArtistManagementService.class);
        when(managementService.effectiveDomain(org.mockito.ArgumentMatchers.any(Artist.class)))
                .thenReturn(ArtistDomainEnum.KEMONO);
        ArtistIconScheduler scheduler = new ArtistIconScheduler(
                mock(KemonoArtistService.class), managementService, properties,
                mock(CookieConfig.class), mock(OkHttpClient.class));

        String icon = scheduler.resolveIcon(new Artist().setType("patreon").setArtistId("84167799"));
        String banner = scheduler.resolveBanner(new Artist().setType("patreon").setArtistId("84167799"));

        assertThat(icon).isEqualTo("https://pawchive.pw/icons/patreon/84167799");
        assertThat(banner).isEqualTo("https://pawchive.pw/banners/patreon/84167799");
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

        ArtistIconScheduler scheduler = new ArtistIconScheduler(
                artistService, managementService, properties, mock(CookieConfig.class), client);
        scheduler.refreshIcons();

        verify(client, times(1)).newCall(any(com.squareup.okhttp.Request.class));
        verify(artistService).updateById(org.mockito.ArgumentMatchers.argThat(updated ->
                "https://img/icon.jpg".equals(updated.getIcon())
                        && "https://img/banner.jpg".equals(updated.getBanner())));
    }
}

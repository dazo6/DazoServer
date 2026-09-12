package com.dazo66.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.service.KemonoArtistService;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Refreshes artist images for the management page once per hour.
 */
@Service
@Slf4j
public class ArtistIconScheduler {

    private final KemonoArtistService artistService;
    private final ArtistManagementService artistManagementService;
    private final KemonoProperties kemonoProperties;
    private final CookieConfig cookieConfig;
    private final OkHttpClient fanboxHttpClient;

    public ArtistIconScheduler(KemonoArtistService artistService,
                               ArtistManagementService artistManagementService,
                               KemonoProperties kemonoProperties,
                               CookieConfig cookieConfig,
                               @Qualifier("fanboxOkHttpClient") OkHttpClient fanboxHttpClient) {
        this.artistService = artistService;
        this.artistManagementService = artistManagementService;
        this.kemonoProperties = kemonoProperties;
        this.cookieConfig = cookieConfig;
        this.fanboxHttpClient = fanboxHttpClient;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void refreshIcons() {
        Page<Artist> page = artistService.getArtistByPage(1, 20000, new QueryWrapper<Artist>());
        List<Artist> artists = page.getRecords();
        if (artists == null || artists.isEmpty()) {
            return;
        }
        for (Artist artist : artists) {
            try {
                ArtistImages images = resolveImages(artist);
                Artist update = new Artist().setId(artist.getId());
                boolean changed = false;
                if (images.icon != null && !images.icon.equals(artist.getIcon())) {
                    update.setIcon(images.icon);
                    changed = true;
                }
                if (images.banner != null && !images.banner.equals(artist.getBanner())) {
                    update.setBanner(images.banner);
                    changed = true;
                }
                if (changed) {
                    artistService.updateById(update);
                }
            } catch (Exception exception) {
                log.warn("刷新画师图片失败: {} ({})", artist.getName(), artist.getArtistId(), exception);
            }
        }
    }

    String resolveIcon(Artist artist) {
        return resolveImages(artist).icon;
    }

    String resolveBanner(Artist artist) {
        return resolveImages(artist).banner;
    }

    private ArtistImages resolveImages(Artist artist) {
        ArtistDomainEnum domain = artistManagementService.effectiveDomain(artist);
        if (domain == ArtistDomainEnum.KEMONO) {
            String type = artist.getType();
            String artistId = artist.getArtistId();
            return new ArtistImages(
                    kemonoProperties.siteUrl("/icons/" + type + "/" + artistId),
                    kemonoProperties.siteUrl("/banners/" + type + "/" + artistId));
        }
        return fetchFanboxImages(artist.getArtistId());
    }

    private ArtistImages fetchFanboxImages(String artistId) {
        try {
            String encodedArtistId = URLEncoder.encode(artistId, StandardCharsets.UTF_8.name());
            Request request = new Request.Builder()
                    .url("https://api.fanbox.cc/creator.get?creatorId=" + encodedArtistId)
                    .addHeader("Cookie", "FANBOXSESSID=" + cookieConfig.getFanboxsessid())
                    .addHeader("Referer", "https://www.fanbox.cc/")
                    .addHeader("Origin", "https://www.fanbox.cc")
                    .build();
            Response response = fanboxHttpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Fanbox creator 接口返回 {}: {}", response.code(), artistId);
                    return ArtistImages.EMPTY;
                }
                JSONObject root = JSON.parseObject(response.body().string());
                JSONObject body = root == null ? null : root.getJSONObject("body");
                JSONObject user = body == null ? null : body.getJSONObject("user");
                return new ArtistImages(
                        user == null ? null : user.getString("iconUrl"),
                        body == null ? null : body.getString("coverImageUrl"));
            } finally {
                if (response.body() != null) {
                    response.body().close();
                }
            }
        } catch (Exception exception) {
            log.warn("请求 Fanbox 画师图片失败: {}", artistId, exception);
            return ArtistImages.EMPTY;
        }
    }

    private static final class ArtistImages {
        private static final ArtistImages EMPTY = new ArtistImages(null, null);
        private final String icon;
        private final String banner;

        private ArtistImages(String icon, String banner) {
            this.icon = icon;
            this.banner = banner;
        }
    }
}

package com.dazo66.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.service.ArtistImageCache;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Refreshes artist images for the management page once per hour.
 */
@Service
@Slf4j
public class ArtistIconScheduler {

    private static final String IMAGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

    private final KemonoArtistService artistService;
    private final ArtistManagementService artistManagementService;
    private final KemonoProperties kemonoProperties;
    private final CookieConfig cookieConfig;
    private final OkHttpClient fanboxHttpClient;
    private final ArtistImageCache imageCache;

    public ArtistIconScheduler(KemonoArtistService artistService,
                               ArtistManagementService artistManagementService,
                               KemonoProperties kemonoProperties,
                               CookieConfig cookieConfig,
                               @Qualifier("fanboxOkHttpClient") OkHttpClient fanboxHttpClient,
                               ArtistImageCache imageCache) {
        this.artistService = artistService;
        this.artistManagementService = artistManagementService;
        this.kemonoProperties = kemonoProperties;
        this.cookieConfig = cookieConfig;
        this.fanboxHttpClient = fanboxHttpClient;
        this.imageCache = imageCache;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void refreshIcons() {
        log.info("开始刷新画师图片缓存");
        Page<Artist> page = artistService.getArtistByPage(1, 20000, new QueryWrapper<Artist>());
        List<Artist> artists = page.getRecords();
        if (artists == null || artists.isEmpty()) {
            log.info("画师图片缓存刷新完成：没有画师需要处理");
            return;
        }
        int updated = 0;
        int failed = 0;
        for (Artist artist : artists) {
            try {
                ArtistImages images = resolveImages(artist);
                Artist update = new Artist().setId(artist.getId());
                boolean changed = false;
                String iconFile = downloadImage(artist, images.icon, "icon");
                String bannerFile = downloadImage(artist, images.banner, "banner");
                if (images.failed || (images.icon != null && iconFile == null)
                        || (images.banner != null && bannerFile == null)) {
                    failed += 1;
                }
                if (iconFile != null && !iconFile.equals(artist.getIcon())) {
                    update.setIcon(iconFile);
                    changed = true;
                }
                if (bannerFile != null && !bannerFile.equals(artist.getBanner())) {
                    update.setBanner(bannerFile);
                    changed = true;
                }
                if (changed) {
                    artistService.updateById(update);
                    if (iconFile != null) {
                        imageCache.deleteIfObsolete(artist.getIcon(), iconFile);
                    }
                    if (bannerFile != null) {
                        imageCache.deleteIfObsolete(artist.getBanner(), bannerFile);
                    }
                    updated += 1;
                }
            } catch (Exception exception) {
                failed += 1;
                log.warn("刷新画师图片失败: {} ({})", artist.getName(), artist.getArtistId(), exception);
            }
        }
        log.info("画师图片缓存刷新完成：共 {} 个画师，更新 {} 个，失败 {} 个",
                artists.size(), updated, failed);
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

    private String downloadImage(Artist artist, String sourceUrl, String kind) {
        if (sourceUrl == null || sourceUrl.trim().isEmpty() || artist.getId() == null) {
            return null;
        }
        try {
            ArtistDomainEnum domain = artistManagementService.effectiveDomain(artist);
            Request.Builder request = new Request.Builder()
                    .url(sourceUrl)
                    .addHeader("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                    .addHeader("User-Agent", IMAGE_USER_AGENT)
                    .addHeader("Referer", domain == ArtistDomainEnum.FANBOX
                            ? "https://www.fanbox.cc/" : kemonoProperties.getBaseUrl());
            String cookie = domain == ArtistDomainEnum.FANBOX
                    ? "FANBOXSESSID=" + cookieConfig.getFanboxsessid()
                    : cookieHeader(cookieConfig.getCookie());
            if (!cookie.isEmpty()) {
                request.addHeader("Cookie", cookie);
            }
            return imageCache.download(fanboxHttpClient, request.build(), artist.getId(), kind);
        } catch (Exception exception) {
            log.warn("下载画师{}失败: {} ({})", "icon".equals(kind) ? "头像" : "横幅",
                    artist.getName(), artist.getArtistId(), exception);
            return null;
        }
    }

    private String cookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
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
                    return ArtistImages.FAILED;
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
            return ArtistImages.FAILED;
        }
    }

    private static final class ArtistImages {
        private static final ArtistImages FAILED = new ArtistImages(null, null, true);
        private final String icon;
        private final String banner;
        private final boolean failed;

        private ArtistImages(String icon, String banner) {
            this(icon, banner, false);
        }

        private ArtistImages(String icon, String banner, boolean failed) {
            this.icon = icon;
            this.banner = banner;
            this.failed = failed;
        }
    }
}

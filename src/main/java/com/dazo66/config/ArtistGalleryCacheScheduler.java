package com.dazo66.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.FreeGallery;
import com.dazo66.service.ArtistGalleryCache;
import com.dazo66.service.FreeGalleryService;
import com.dazo66.service.KemonoArtistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rebuilds all gallery JSON indexes every day at 13:15 in China Standard Time.
 */
@Service
@Slf4j
public class ArtistGalleryCacheScheduler {

    private static final int PAGE_SIZE = 500;

    private final KemonoArtistService artistService;
    private final ArtistGalleryCache galleryCache;
    private final FreeGalleryService freeGalleryService;

    public ArtistGalleryCacheScheduler(KemonoArtistService artistService, ArtistGalleryCache galleryCache,
                                       FreeGalleryService freeGalleryService) {
        this.artistService = artistService;
        this.galleryCache = galleryCache;
        this.freeGalleryService = freeGalleryService;
    }

    @Scheduled(cron = "0 15 13 * * ?", zone = "Asia/Shanghai")
    public void rebuildAll() {
        log.info("开始刷新全部画师图库缓存");
        int pageNumber = 1;
        int processed = 0;
        int failed = 0;
        while (true) {
            Page<Artist> page = artistService.getArtistByPage(pageNumber, PAGE_SIZE,
                    new QueryWrapper<Artist>().orderByAsc("id"));
            List<Artist> artists = page.getRecords();
            if (artists == null || artists.isEmpty()) {
                break;
            }
            for (Artist artist : artists) {
                try {
                    galleryCache.rebuildScheduledTree(artist);
                    processed += 1;
                } catch (Exception exception) {
                    failed += 1;
                    log.warn("刷新画师图库缓存失败: {} ({})", artist.getName(), artist.getId(), exception);
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber += 1;
        }
        for (FreeGallery freeGallery : freeGalleryService.list(null)) {
            try {
                galleryCache.rebuildScheduledTree(freeGallery.getFolderPath());
                processed += 1;
            } catch (Exception exception) {
                failed += 1;
                log.warn("刷新自由画廊缓存失败: {} ({})", freeGallery.getName(), freeGallery.getId(), exception);
            }
        }
        log.info("全部图库缓存刷新完成：成功 {} 个，失败 {} 个", processed, failed);
    }
}

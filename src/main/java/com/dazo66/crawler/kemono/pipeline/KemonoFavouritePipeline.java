package com.dazo66.crawler.kemono.pipeline;

import com.dazo66.config.KemonoProperties;
import com.dazo66.crawler.kemono.KemonoFavourite;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.service.KemonoArtistService;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.scheduler.DeriveSchedulerContext;
import com.geccocrawler.gecco.spider.SpiderBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Date;
import java.util.Map;

@Service
@Slf4j
public class KemonoFavouritePipeline implements Pipeline<SpiderBean> {

    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private KemonoProperties kemonoProperties;

    @Override
    public void process(SpiderBean bean) {
        KemonoFavourite kemonoFavourite = (KemonoFavourite) bean;
        if (StringUtils.isNotBlank(kemonoFavourite.getNextPage())) {
            DeriveSchedulerContext.into(new HttpGetRequest(kemonoProperties.absoluteUrl(kemonoFavourite.getNextPage())));
        }
        if (kemonoFavourite.getArtistUrls() == null || kemonoFavourite.getArtistNames() == null) {
            log.warn("收藏页未解析到作者，请检查登录 cookie 或页面结构");
            return;
        }
        int artistCount = Math.min(kemonoFavourite.getArtistUrls().size(),
                kemonoFavourite.getArtistNames().size());
        for (int i = 0; i < artistCount; i++) {
            AntPathMatcher matcher = new AntPathMatcher();
            String url = kemonoFavourite.getArtistUrls().get(i);
            String relativeUrl = kemonoProperties.relativePath(url).replaceAll("/+$", "");
            Map<String, String> uriTemplateVariables = matcher.extractUriTemplateVariables("/{type}/user/{id}", relativeUrl);
            if (uriTemplateVariables.size() == 2 && uriTemplateVariables.containsKey("type") && uriTemplateVariables.containsKey("id")) {
                String type = uriTemplateVariables.get("type");
                String artistId = uriTemplateVariables.get("id");
                String artistName = kemonoFavourite.getArtistNames().get(i);
                if (kemonoArtistService.getArtistId(artistId, ArtistDomainEnum.KEMONO) == null) {
                    Artist artist = new Artist();
                    artist.setArtistId(artistId);
                    artist.setEnable(true);
                    artist.setName(artistName);
                    artist.setType(type);
                    artist.setGmtCreate(new Date());
                    kemonoArtistService.addArtist(artist);
                    log.info("自动订阅作者成功: {},", artist);
                }
            } else {
                log.error("不支持的作者链接格式：{}", url);
            }
        }

    }
}

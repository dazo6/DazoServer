package com.dazo66.crawler.kemono.pipeline;

import com.dazo66.crawler.kemono.KemonoFavourite;
import com.dazo66.entity.KemonoArtist;
import com.dazo66.service.KemonoArtistService;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.scheduler.DeriveSchedulerContext;
import com.geccocrawler.gecco.spider.SpiderBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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

    @Override
    public void process(SpiderBean bean) {
        KemonoFavourite kemonoFavourite = (KemonoFavourite) bean;
        if (StringUtils.isNotBlank(kemonoFavourite.getNextPage())) {
            DeriveSchedulerContext.into(new HttpGetRequest(kemonoFavourite.getNextPage()));
        }
        for (int i = 0; i < kemonoFavourite.getArtistUrls().size(); i++) {
            AntPathMatcher matcher = new AntPathMatcher();
            String url = kemonoFavourite.getArtistUrls().get(i);
            Map<String, String> uriTemplateVariables = matcher.extractUriTemplateVariables("https://kemono.su/{type}/user/{id}", url);
            if (uriTemplateVariables.size() == 2 && uriTemplateVariables.containsKey("type") && uriTemplateVariables.containsKey("id")) {
                String type = uriTemplateVariables.get("type");
                String artistId = uriTemplateVariables.get("id");
                String artistName = kemonoFavourite.getArtistNames().get(i);
                if (kemonoArtistService.getArtistId(artistId) == null) {
                    KemonoArtist kemonoArtist = new KemonoArtist();
                    kemonoArtist.setArtistId(artistId);
                    kemonoArtist.setEnable(true);
                    kemonoArtist.setName(artistName);
                    kemonoArtist.setType(type);
                    kemonoArtist.setGmtCreate(new Date());
                    kemonoArtistService.addArtist(kemonoArtist);
                    log.info("自动订阅作者成功: {},", kemonoArtist);
                }
            } else {
                log.error("不支持的作者链接格式：{}", url);
            }
        }

    }
}

package com.dazo66.service;

import com.dazo66.crawler.FanboxFavourite;
import com.dazo66.entity.FanboxArtist;
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
public class FanboxFavouritePipeline implements Pipeline<SpiderBean> {

    @Autowired
    private FanboxArtistService fanboxArtistService;

    @Override
    public void process(SpiderBean bean) {
        FanboxFavourite fanboxFavourite = (FanboxFavourite) bean;
        if (StringUtils.isNotBlank(fanboxFavourite.getNextPage())) {
            DeriveSchedulerContext.into(new HttpGetRequest(fanboxFavourite.getNextPage()));
        }
        for (int i = 0; i < fanboxFavourite.getArtistUrls().size(); i++) {
            AntPathMatcher matcher = new AntPathMatcher();
            String url = fanboxFavourite.getArtistUrls().get(i);
            Map<String, String> uriTemplateVariables = matcher.extractUriTemplateVariables("https://kemono.party/{type}/user/{id}", url);
            if (uriTemplateVariables.size() == 2 && uriTemplateVariables.containsKey("type") && uriTemplateVariables.containsKey("id")) {
                String type = uriTemplateVariables.get("type");
                String artistId = uriTemplateVariables.get("id");
                String artistName = fanboxFavourite.getArtistNames().get(i);
                if (fanboxArtistService.getArtistId(artistId) == null) {
                    FanboxArtist fanboxArtist = new FanboxArtist();
                    fanboxArtist.setArtistId(artistId);
                    fanboxArtist.setEnable(true);
                    fanboxArtist.setName(artistName);
                    fanboxArtist.setType(type);
                    fanboxArtist.setGmtCreate(new Date());
                    fanboxArtistService.addArtist(fanboxArtist);
                    log.info("自动订阅作者成功: {},", fanboxArtist);
                }
            } else {
                log.error("不支持的作者链接格式：{}", url);
            }
        }

    }
}

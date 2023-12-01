package com.dazo66.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.KemonoArtist;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.crawler.kemono.pipeline.KemonoScheduler;
import com.dazo66.util.ResultEntity;
import com.geccocrawler.gecco.GeccoEngine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Date;

/**
 * @author Dazo66
 */
@RestController
@RequestMapping("api/fanbox")
public class FanboxController {

    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Autowired
    private KemonoScheduler kemonoScheduler;

    @GetMapping
    public ResultEntity<Page<KemonoArtist>> getFanboxArtist(@RequestParam(required = false,
            defaultValue = "1") int page, @RequestParam(required = false, defaultValue = "100") int pageSize) {
        return ResultEntity.successWithData(kemonoArtistService.getArtistByPage(page, pageSize,
                new QueryWrapper<KemonoArtist>().orderByDesc("last_update")));
    }

    @PostMapping
    public ResultEntity<KemonoArtist> insert(@RequestBody KemonoArtist kemonoArtist) {
        kemonoArtist.setGmtCreate(new Date());
        if (StringUtils.isEmpty(kemonoArtist.getType())) {
            kemonoArtist.setType("fanbox");
        }
        kemonoArtist.setEnable(true);
        kemonoArtistService.addArtist(kemonoArtist);
        GeccoEngine geccoEngine =
                kemonoScheduler.buildArtistGecco(Collections.singletonList(kemonoArtist));
        if (geccoEngine != null) {
            geccoEngine.start();
        }
        return ResultEntity.successWithData(kemonoArtist);
    }

    @PostMapping("status")
    public ResultEntity<KemonoArtist> setStatus(String artistId, @RequestParam(required = false,
            defaultValue = "false") boolean status) {
        return ResultEntity.successWithData(kemonoArtistService.updateByArtistId(new KemonoArtist().setArtistId(artistId).setEnable(status)));
    }

    @PostMapping("clean/history")
    public ResultEntity<Boolean> cleanHistory() {
        return ResultEntity.successWithData(crawlerRequestService.cleanAll());
    }
}

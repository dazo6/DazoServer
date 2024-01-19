package com.dazo66.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.config.KemonoScheduler;
import com.dazo66.entity.Artist;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.ResultEntity;
import com.geccocrawler.gecco.GeccoEngine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public ResultEntity<Page<Artist>> getFanboxArtist(@RequestParam(required = false,
            defaultValue = "1") int page, @RequestParam(required = false, defaultValue = "100") int pageSize) {
        return ResultEntity.successWithData(kemonoArtistService.getArtistByPage(page, pageSize,
                new QueryWrapper<Artist>().orderByDesc("last_update")));
    }

    @PostMapping
    public ResultEntity<Artist> insert(@RequestBody Artist artist) {
        artist.setGmtCreate(new Date());
        if (StringUtils.isEmpty(artist.getType())) {
            artist.setType("fanbox");
        }
        artist.setEnable(true);
        kemonoArtistService.addArtist(artist);
        GeccoEngine geccoEngine =
                kemonoScheduler.buildArtistGecco(Collections.singletonList(artist));
        if (geccoEngine != null) {
            geccoEngine.start();
        }
        return ResultEntity.successWithData(artist);
    }

    @PostMapping("status")
    public ResultEntity<Artist> setStatus(String artistId, @RequestParam(required = false,
            defaultValue = "false") boolean status) {
        return ResultEntity.successWithData(kemonoArtistService.updateByArtistId(new Artist().setArtistId(artistId).setEnable(status)));
    }

    @PostMapping("clean/history")
    public ResultEntity<Boolean> cleanHistory() {
        return ResultEntity.successWithData(crawlerRequestService.cleanAll());
    }
}

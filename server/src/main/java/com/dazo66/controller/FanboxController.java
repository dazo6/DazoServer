package com.dazo66.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.FanboxArtist;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.FanboxArtistService;
import com.dazo66.service.FanboxScheduler;
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
    private FanboxArtistService fanboxArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;
    @Autowired
    private FanboxScheduler fanboxScheduler;

    @GetMapping
    public ResultEntity<Page<FanboxArtist>> getFanboxArtist(@RequestParam(required = false,
            defaultValue = "1") int page, @RequestParam(required = false, defaultValue = "100") int pageSize) {
        return ResultEntity.successWithData(fanboxArtistService.getArtistByPage(page, pageSize,
                new QueryWrapper<FanboxArtist>().orderByDesc("last_update")));
    }

    @PostMapping
    public ResultEntity<FanboxArtist> insert(@RequestBody FanboxArtist fanboxArtist) {
        fanboxArtist.setGmtCreate(new Date());
        if (StringUtils.isEmpty(fanboxArtist.getType())) {
            fanboxArtist.setType("fanbox");
        }
        fanboxArtist.setEnable(true);
        fanboxArtistService.addArtist(fanboxArtist);
        GeccoEngine geccoEngine =
                fanboxScheduler.buildArtistGecco(Collections.singletonList(fanboxArtist));
        if (geccoEngine != null) {
            geccoEngine.start();
        }
        return ResultEntity.successWithData(fanboxArtist);
    }

    @PostMapping("status")
    public ResultEntity<FanboxArtist> setStatus(String artistId, @RequestParam(required = false,
            defaultValue = "false") boolean status) {
        return ResultEntity.successWithData(fanboxArtistService.updateByArtistId(new FanboxArtist().setArtistId(artistId).setEnable(status)));
    }

    @PostMapping("clean/history")
    public ResultEntity<Integer> cleanHistory() {
        return ResultEntity.successWithData(crawlerRequestService.cleanAll());
    }
}

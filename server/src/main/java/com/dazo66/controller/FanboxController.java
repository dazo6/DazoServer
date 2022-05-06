package com.dazo66.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.FanboxArtist;
import com.dazo66.util.ResultEntity;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.FanboxArtistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public ResultEntity<Page<FanboxArtist>> getFanboxArtist(@RequestParam(required = false,
            defaultValue = "1") int page, @RequestParam(required = false, defaultValue = "100") int pageSize) {
        return ResultEntity.successWithData(fanboxArtistService.getArtistByPage(page, pageSize,
                new QueryWrapper<>()));
    }

    @PostMapping
    public ResultEntity<FanboxArtist> insert(@RequestBody FanboxArtist fanboxArtist) {
        fanboxArtist.setGmtCreate(new Date());
        return ResultEntity.successWithData(fanboxArtistService.addArtist(fanboxArtist));
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

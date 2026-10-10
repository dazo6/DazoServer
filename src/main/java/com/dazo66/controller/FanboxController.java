package com.dazo66.controller;


import com.dazo66.security.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.config.KemonoScheduler;
import com.dazo66.dashboard.ArtistCreateRequest;
import com.dazo66.dashboard.ArtistManagementItem;
import com.dazo66.entity.Artist;
import com.dazo66.service.ArtistManagementService;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.ResultEntity;
import com.geccocrawler.gecco.GeccoEngine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Date;
import java.util.stream.Collectors;

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
    @Autowired
    private ArtistManagementService artistManagementService;

    @GetMapping
    public ResultEntity<Page<ArtistManagementItem>> getFanboxArtist(@RequestParam(required = false,
            defaultValue = "1") int page,
                                                                    @RequestParam(required = false, defaultValue = "100") int pageSize,
                                                                    @RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) Boolean enabled,
                                                                    @RequestParam(required = false, defaultValue = "desc") String lastUpdateOrder) {
        QueryWrapper<Artist> query = new QueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            query.and(wrapper -> wrapper.like("name", keyword).or().like("artist_id", keyword));
        }
        if (enabled != null) {
            query.eq("enable", enabled);
        }
        boolean ascending = "asc".equalsIgnoreCase(lastUpdateOrder);
        query.orderBy(true, ascending, "last_update").orderByDesc("id");
        CurrentUser.require();
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        Page<Artist> artistPage = kemonoArtistService.getArtistByPage(Math.max(page, 1), safePageSize, query);
        Page<ArtistManagementItem> result = new Page<>(artistPage.getCurrent(), artistPage.getSize(),
                artistPage.getTotal());
        result.setRecords(artistPage.getRecords().stream().map(artistManagementService::toItem)
                .collect(Collectors.toList()));
        return ResultEntity.successWithData(result);
    }

    @PostMapping
    @ACL(type=AclType.UPDATE,point="op:artist.create")
    public ResultEntity<ArtistManagementItem> insert(@RequestBody ArtistCreateRequest request) {
        Artist artist;
        try {
            artist = artistManagementService.parse(request);
        } catch (IllegalArgumentException exception) {
            return ResultEntity.failed(exception.getMessage());
        }
        AclAccess.checkPath(AclType.UPDATE,artistManagementService.resolveFolderPath(artist));
        if (kemonoArtistService.getArtistId(artist.getArtistId()) != null) {
            return ResultEntity.failed("该画师已经存在");
        }
        artist.setGmtCreate(new Date());
        kemonoArtistService.addArtist(artist);
        GeccoEngine geccoEngine =
                kemonoScheduler.buildArtistGecco(Collections.singletonList(artist));
        if (geccoEngine != null) {
            geccoEngine.start();
        }
        return ResultEntity.successWithData(artistManagementService.toItem(artist));
    }

    @GetMapping("{id}")
    public ResponseEntity<ResultEntity<ArtistManagementItem>> getArtist(@PathVariable int id) {
        Artist artist = kemonoArtistService.getById(id);
        if (artist == null) {
            return ResponseEntity.notFound().build();
        }
        AclAccess.checkPath(AclType.READ,artistManagementService.resolveFolderPath(artist));
        return ResponseEntity.ok(ResultEntity.successWithData(artistManagementService.toItem(artist)));
    }

    @PostMapping("status")
    @ACL(type=AclType.UPDATE,point="op:artist.status")
    public ResultEntity<Artist> setStatus(String artistId, @RequestParam(required = false,
            defaultValue = "false") boolean status) {
        Artist existing=kemonoArtistService.getArtistId(artistId);
        requireArtist(existing,AclType.UPDATE);
        return ResultEntity.successWithData(kemonoArtistService.updateByArtistId(new Artist().setArtistId(artistId).setEnable(status)));
    }

    @PostMapping("{id}/status")
    @ACL(type=AclType.UPDATE,point="op:artist.status")
    public ResultEntity<Boolean> setStatusById(@PathVariable int id, @RequestParam boolean enabled) {
        requireArtist(kemonoArtistService.getById(id),AclType.UPDATE);
        kemonoArtistService.updateById(new Artist().setId(id).setEnable(enabled));
        return ResultEntity.successWithData(true);
    }

    @PostMapping("{id}/compression")
    @ACL(type=AclType.UPDATE,point="op:artist.compression")
    public ResultEntity<Boolean> setCompressionById(@PathVariable int id,
                                                    @RequestParam boolean enabled) {
        requireArtist(kemonoArtistService.getById(id),AclType.UPDATE);
        kemonoArtistService.updateById(new Artist().setId(id).setCompression(enabled));
        return ResultEntity.successWithData(true);
    }

    private void requireArtist(Artist a,AclType t) {
        if(a==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"画师不存在");
        AclAccess.checkPath(t,artistManagementService.resolveFolderPath(a));
    }

    @PostMapping("clean/history")
    @ACL(type=AclType.DELETE,point="op:crawlHistory.clean")
    @ACL(type=AclType.DELETE,point="*")
    public ResultEntity<Boolean> cleanHistory() {
        return ResultEntity.successWithData(crawlerRequestService.cleanAll());
    }
}

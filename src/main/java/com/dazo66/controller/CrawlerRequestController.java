package com.dazo66.controller;

import com.dazo66.security.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.config.KemonoProperties;
import com.dazo66.dashboard.CrawlerRequestHistoryItem;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.util.ResultEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Date;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/crawler-requests")
public class CrawlerRequestController {

    private final CrawlerRequestService crawlerRequestService;
    private final KemonoProperties kemonoProperties;

    public CrawlerRequestController(CrawlerRequestService crawlerRequestService,
                                    KemonoProperties kemonoProperties) {
        this.crawlerRequestService = crawlerRequestService;
        this.kemonoProperties = kemonoProperties;
    }

    @GetMapping
    @ACL(type=AclType.READ,point="op:crawlHistory.list")
    @ACL(type=AclType.READ,point="*")
    public ResultEntity<Page<CrawlerRequestHistoryItem>> getHistory(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean done,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date start,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date end) {
        QueryWrapper<CrawlerRequest> query = new QueryWrapper<>();
        query.notLike("url", "?o=");
        if (StringUtils.isNotBlank(keyword)) {
            query.like("url", normalizeKeyword(keyword));
        }
        if (done != null) {
            query.eq("is_done", done);
        }
        if (start != null) {
            query.ge("gmt_create", start);
        }
        if (end != null) {
            query.le("gmt_create", end);
        }
        query.orderByDesc("gmt_create").orderByDesc("id");

        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        Page<CrawlerRequest> source = crawlerRequestService.getPage(
                Page.of(Math.max(page, 1), safePageSize), query);
        Page<CrawlerRequestHistoryItem> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toHistoryItem).collect(Collectors.toList()));
        return ResultEntity.successWithData(result);
    }

    private CrawlerRequestHistoryItem toHistoryItem(CrawlerRequest request) {
        CrawlerRequestHistoryItem item = new CrawlerRequestHistoryItem();
        item.setId(request.getId());
        item.setArtistId(request.getArtistId());
        item.setUrl(completeUrl(request.getUrl()));
        item.setGmtCreate(request.getGmtCreate());
        item.setIsDone(request.getIsDone());
        item.setErrorCount(request.getErrorCount());
        return item;
    }

    private String completeUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        try {
            return URI.create(url).isAbsolute() ? url : kemonoProperties.absoluteUrl(url);
        } catch (IllegalArgumentException ignored) {
            return url;
        }
    }

    private String normalizeKeyword(String keyword) {
        if (kemonoProperties.isConfiguredOrigin(keyword)) {
            return kemonoProperties.relativePath(keyword);
        }
        return keyword;
    }
}

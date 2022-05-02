package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.mapper.CrawlerRequestMapper;
import com.dazo66.service.CrawlerRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author Dazo66
 */
@Service
public class CrawlerRequestServiceImpl implements CrawlerRequestService {

    @Autowired
    private CrawlerRequestMapper crawlerRequestMapper;

    @Override
    public boolean check(String url) {
        CrawlerRequest crawlerRequest = new CrawlerRequest();
        crawlerRequest.setUrl(url);
        return crawlerRequestMapper.selectOne(new QueryWrapper<>(crawlerRequest)) == null;
    }

    @Override
    public CrawlerRequest add(String url) {
        CrawlerRequest crawlerRequest = new CrawlerRequest();
        crawlerRequest.setUrl(url);
        crawlerRequest.setGmtCreate(new Date());
        crawlerRequestMapper.insert(crawlerRequest);
        return crawlerRequest;
    }

    @Override
    public int cleanAll() {
        return crawlerRequestMapper.delete(new QueryWrapper<>());
    }

}

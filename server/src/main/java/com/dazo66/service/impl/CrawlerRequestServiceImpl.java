package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.mapper.CrawlerRequestMapper;
import com.dazo66.repository.CrawlerRequestRepository;
import com.dazo66.service.CrawlerRequestService;
import com.geccocrawler.gecco.utils.UrlMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 * @author Dazo66
 */
@Service
public class CrawlerRequestServiceImpl implements CrawlerRequestService {

    @Autowired
    private CrawlerRequestRepository crawlerRequestRepository;

    @Override
    public boolean check(String url) {
        CrawlerRequest crawlerRequest = new CrawlerRequest();
        crawlerRequest.setUrl(url);
        return crawlerRequestRepository.getOne(new QueryWrapper<>(crawlerRequest)) == null;
    }

    @Override
    public CrawlerRequest add(String url) {
        CrawlerRequest crawlerRequest = new CrawlerRequest();
        crawlerRequest.setUrl(url);
        crawlerRequest.setGmtCreate(new Date());
        crawlerRequest.setIsDone(false);
        crawlerRequestRepository.saveOrUpdate(crawlerRequest);
        return crawlerRequest;
    }

    @Override
    public Boolean cleanAll() {
        return crawlerRequestRepository.remove(new QueryWrapper<>());
    }

    @Override
    public Boolean updateByUrl(CrawlerRequest crawlerRequest) {
        return crawlerRequestRepository.saveOrUpdate(crawlerRequest,
                new QueryWrapper<>(new CrawlerRequest().setUrl(crawlerRequest.getUrl())));
    }

    @Override
    public Page<CrawlerRequest> getByPage(int page, int pageSize,
                                          QueryWrapper<CrawlerRequest> queryWrapper) {
        return crawlerRequestRepository.page(Page.of(page, pageSize), queryWrapper);
    }

}

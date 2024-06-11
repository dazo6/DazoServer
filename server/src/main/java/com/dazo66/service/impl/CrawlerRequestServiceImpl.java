package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.repository.CrawlerRequestRepository;
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
        crawlerRequest.setErrorCount(0);
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
    public LambdaQueryChainWrapper<CrawlerRequest> getLambdaQuery() {
        return crawlerRequestRepository.lambdaQuery();
    }

    @Override
    public CrawlerRequest getByUrl(String url) {
        return crawlerRequestRepository.lambdaQuery().eq(CrawlerRequest::getUrl, url).one();
    }

}

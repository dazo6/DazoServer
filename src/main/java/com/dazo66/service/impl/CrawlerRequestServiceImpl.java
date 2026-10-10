package com.dazo66.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.mapper.FanboxArtistMapper;
import com.dazo66.config.KemonoProperties;
import com.dazo66.util.CrawlerArtistUrl;
import java.util.List;
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

    @Autowired
    private FanboxArtistMapper artistMapper;
    @Autowired
    private KemonoProperties kemonoProperties;

    private void attachArtist(CrawlerRequest request) {
        if (request.getArtistId() != null) return;
        CrawlerArtistUrl.Identity identity = CrawlerArtistUrl.parse(request.getUrl(), kemonoProperties.getBaseUrl());
        if (identity == null) return;
        QueryWrapper<Artist> query = new QueryWrapper<Artist>()
                .eq("artist_id", identity.externalId).eq("domain", ArtistDomainEnum.valueOf(identity.domain));
        if (identity.type != null) query.eq("type", identity.type);
        List<Artist> matches = artistMapper.selectList(query);
        if (matches.size() == 1) request.setArtistId(matches.get(0).getId());
    }

    @Override
    public boolean check(String url) {
        CrawlerRequest crawlerRequest = new CrawlerRequest();
        crawlerRequest.setUrl(url);
        return crawlerRequestRepository.getOne(new QueryWrapper<>(crawlerRequest)) == null;
    }

    @Override
    public CrawlerRequest add(String url) {
        return add(url, null);
    }

    @Override
    public CrawlerRequest add(String url, Integer artistId) {
        CrawlerRequest crawlerRequest = new CrawlerRequest();
        crawlerRequest.setUrl(url);
        crawlerRequest.setArtistId(artistId);
        attachArtist(crawlerRequest);
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
        attachArtist(crawlerRequest);
        return crawlerRequestRepository.saveOrUpdate(crawlerRequest,
                new QueryWrapper<>(new CrawlerRequest().setUrl(crawlerRequest.getUrl())));
    }

    @Override
    public LambdaQueryChainWrapper<CrawlerRequest> getLambdaQuery() {
        return crawlerRequestRepository.lambdaQuery();
    }

    @Override
    public Page<CrawlerRequest> getPage(Page<CrawlerRequest> page, Wrapper<CrawlerRequest> queryWrapper) {
        return crawlerRequestRepository.page(page, queryWrapper);
    }

    @Override
    public CrawlerRequest getByUrl(String url) {
        return crawlerRequestRepository.lambdaQuery().eq(CrawlerRequest::getUrl, url).one();
    }

}

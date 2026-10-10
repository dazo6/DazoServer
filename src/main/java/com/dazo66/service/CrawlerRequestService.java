package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.CrawlerRequest;

/**
 * @author Dazo66
 */
public interface CrawlerRequestService {

    /**
     * 检查是否已经爬过了
     *
     * @param url 爬虫的url
     * @return 是否存在在库中
     */
    boolean check(String url);

    CrawlerRequest add(String url);

    CrawlerRequest add(String url, Integer artistId);

    Boolean cleanAll();

    Boolean updateByUrl(CrawlerRequest crawlerRequest);

    LambdaQueryChainWrapper<CrawlerRequest> getLambdaQuery();

    Page<CrawlerRequest> getPage(Page<CrawlerRequest> page, Wrapper<CrawlerRequest> queryWrapper);

    CrawlerRequest getByUrl(String url);
}

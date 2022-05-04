package com.dazo66.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.config.GeccoConfig;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.entity.FanboxArtist;
import com.dazo66.util.SpringContextUtils;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.geccocrawler.gecco.spring.SpringPipelineFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Dazo66
 */
@Service
@AutoConfigureAfter(GeccoConfig.class)
public class FanboxScheduler {

    @Autowired
    private FanboxArtistService fanboxArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @Scheduled(initialDelay = 0, fixedDelay = 6, timeUnit = TimeUnit.HOURS)
    public void schedulerSearchNeedPost() {
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        Page<FanboxArtist> artistByPage = fanboxArtistService.getArtistByPage(1,
                Integer.MAX_VALUE, new QueryWrapper<>(new FanboxArtist().setEnable(true)));
        List<HttpRequest> httpGetRequestList =
                artistByPage.getRecords().stream().map(artist -> new HttpGetRequest("https" +
                        "://kemono.party/fanbox/user/" + artist.getArtistId())).collect(Collectors.toList());
        GeccoEngine.create()
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start(httpGetRequestList).pipelineFactory(springPipelineFactory).scheduler(new UniqueSpiderScheduler())
                //开启几个爬虫线程
                .thread(5)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false)
                //阻塞方式运行
                .run();
    }

    @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void schedulerDownloadUrl() {
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        Page<CrawlerRequest> crawlerRequestByPage = crawlerRequestService.getByPage(1, 20,
                new QueryWrapper<>(new CrawlerRequest().setIsDone(false)).orderByAsc("random()"));
        List<HttpRequest> httpGetRequests =
                crawlerRequestByPage.getRecords().stream().map(crawlerRequest -> new HttpGetRequest(crawlerRequest.getUrl())).collect(Collectors.toList());
        GeccoEngine.create()
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start(httpGetRequests).pipelineFactory(springPipelineFactory).scheduler(new UniqueSpiderScheduler())
                //开启几个爬虫线程
                .thread(10)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false)
                //阻塞方式运行
                .run();
    }


}

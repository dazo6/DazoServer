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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FanboxScheduler {

    @Autowired
    private FanboxArtistService fanboxArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void schedulerSearchNeedPost() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildArtistGecco();
        if (geccoEngine != null) {
            geccoEngine.run();
        }
    }

    public GeccoEngine buildArtistGecco() {
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        Page<FanboxArtist> artistByPage = fanboxArtistService.getArtistByPage(1,
                Integer.MAX_VALUE, new QueryWrapper<>(new FanboxArtist().setEnable(true)));
        if (artistByPage.getRecords().size() == 0) {
            log.info("作者队列为空，不需要爬取");
            return null;
        }
        List<HttpRequest> httpGetRequestList =
                artistByPage.getRecords().stream().map(artist -> new HttpGetRequest(String.format("https://kemono.party/%s/user/%s", artist.getType(), artist.getArtistId()))).collect(Collectors.toList());
        return GeccoEngine.create()
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
                .mobile(false);
    }

    @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void schedulerDownloadUrl() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildImageGecco();
        if (geccoEngine != null) {
            geccoEngine.run();
        }
    }

    public GeccoEngine buildImageGecco() {
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        Page<CrawlerRequest> crawlerRequestByPage = crawlerRequestService.getByPage(1, 20,
                new QueryWrapper<>(new CrawlerRequest().setIsDone(false)).orderByAsc("rand()"));
        if (crawlerRequestByPage.getRecords().size() == 0) {
            log.info("爬取队列为空，不需要爬取");
            return null;
        }
        List<HttpRequest> httpGetRequests =
                crawlerRequestByPage.getRecords().stream().map(crawlerRequest -> new HttpGetRequest(crawlerRequest.getUrl())).collect(Collectors.toList());
        return GeccoEngine.create()
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
                .mobile(false);
    }


}

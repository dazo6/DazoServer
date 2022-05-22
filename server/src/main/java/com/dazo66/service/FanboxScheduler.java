package com.dazo66.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private CloseableHttpClient fanboxHttpClient;

    @Scheduled(initialDelay = 0, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void schedulerSearchNeedPost() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildArtistGecco(null);
        if (geccoEngine != null) {
            geccoEngine.run();
        }
    }

    public GeccoEngine buildArtistGecco(List<FanboxArtist> artists) {
        if (artists == null) {
            Page<FanboxArtist> artistByPage = fanboxArtistService.getArtistByPage(1, 20,
                    new QueryWrapper<>(new FanboxArtist().setEnable(true)).orderByAsc("rand()"));
            artists = artistByPage.getRecords();
        }
        if (artists.size() == 0) {
            log.info("作者队列为空，不需要爬取");
            return null;
        }
        List<HttpRequest> httpGetRequestList =
                artists.stream().map(artist -> new HttpGetRequest(String.format("https://kemono" + ".party/%s/user/%s", artist.getType(), artist.getArtistId()))).collect(Collectors.toList());
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
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

    @SneakyThrows
    @Scheduled(initialDelay = 0, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void autoSubKemono() {
        HttpGet getFavoriteRequest = new HttpGet("https://kemono.party/api/favorites?type=artist");
        HttpResponse getFavoriteResponse = fanboxHttpClient.execute(getFavoriteRequest);
        Set<FanboxArtist> ids =
                JSON.parseArray(IOUtils.toString(getFavoriteResponse.getEntity().getContent())).stream().map(o -> new FanboxArtist().setArtistId(((JSONObject) o).getString("id")).setType(((JSONObject) o).getString("service")).setName(((JSONObject) o).getString("name"))).collect(Collectors.toSet());
        getFavoriteResponse.getEntity().getContent().close();
        Page<FanboxArtist> artistByPage = fanboxArtistService.getArtistByPage(1,
                Integer.MAX_VALUE, new QueryWrapper<>(new FanboxArtist()));
        Set<String> set =
                artistByPage.getRecords().stream().map(artist -> artist.getArtistId()).collect(Collectors.toSet());
        ids.removeIf(artist -> set.contains(artist.getArtistId()));
        if (!ids.isEmpty()) {
            log.info("不在库里的作者有：{}", ids);
            ids.forEach(artist -> {
                artist.setGmtCreate(new Date());
                artist.setEnable(true);
                fanboxArtistService.addArtist(artist);
            });
        }
    }


}

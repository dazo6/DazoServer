package com.dazo66.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.FanboxHelper;
import com.dazo66.util.SpringContextUtils;
import com.dazo66.util.StringUtil;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.downloader.proxy.Proxys;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.geccocrawler.gecco.spring.SpringPipelineFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Dazo66
 */
@Service
@AutoConfigureAfter(GeccoConfig.class)
@Slf4j
public class KemonoScheduler {

    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @Value("${fanbox.save.path}")
    private String fileSavePath;
    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;

    // @Scheduled(initialDelay = 0, fixedDelay = 20, timeUnit = TimeUnit.MINUTES)
    public void fixData() {
        crawlerRequestService.getLambdaQuery().like(CrawlerRequest::getUrl, "kemono").like(CrawlerRequest::getUrl, "post/").list().forEach(crawlerRequest -> {
            if (!crawlerRequest.getUrl().contains("/api/v1/")) {
                crawlerRequest.setUrl(crawlerRequest.getUrl().replace("https://kemono.su/", "https://kemono.su/api/v1/"));
                crawlerRequestService.updateByUrl(crawlerRequest);
                log.info("fix url: {}", crawlerRequest.getUrl());
            }
        });
    }

    // kemono 挂了 暂停这个定时任务
    @Scheduled(initialDelay = 0, fixedDelay = 20, timeUnit = TimeUnit.MINUTES)
    public void autoSubKemono() {
        log.info("开始自动订阅作者！");
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        GeccoEngine.create()
                // post__attachment-link
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start("https://kemono.su/favorites").pipelineFactory(springPipelineFactory)
                //开启几个爬虫线程
                .thread(2)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false)
                //阻塞方式运行
                .run();
    }

    @Scheduled(initialDelay = 0, fixedDelay = 20, timeUnit = TimeUnit.MINUTES)
    public void autoUnsubArtist() {
        File[] files = new File(fileSavePath).listFiles();
        Set<String> artistSet = Arrays.stream(files).map(File::getName).collect(Collectors.toSet());
        LambdaQueryWrapper<Artist> query = new LambdaQueryWrapper<Artist>().isNotNull(Artist::getLastUpdate).eq(Artist::getEnable, true);
        Page<Artist> unableArtist = kemonoArtistService.getArtistByPage(1, 20000, query);
        for (Artist record : unableArtist.getRecords()) {
            if (!artistSet.contains(StringUtil.cleanSym(record.getName()))) {
                record.setEnable(false);
                //fanboxArtistService.updateByArtistId(record);
                log.info("unable artist: {}", record);
            }
        }

    }

    @Scheduled(initialDelay = 0, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void schedulerSearchNeedPost() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildArtistGecco(null);
        if (geccoEngine != null) {
            geccoEngine.run();
        }
    }

    public GeccoEngine buildArtistGecco(List<Artist> artists) {
        if (artists == null) {
            artists = new ArrayList<>();
            Page<Artist> artistByPage = kemonoArtistService.getArtistByPage(1, 20, new QueryWrapper<>(new Artist().setArtistId("bttn")));
            artists.addAll(artistByPage.getRecords());
            artistByPage = kemonoArtistService.getArtistByPage(1, 20,
                    new QueryWrapper<>(new Artist().setEnable(true).setDomain(ArtistDomainEnum.KEMONO)).orderByAsc("rand()"));
            artists.addAll(artistByPage.getRecords());
            artistByPage = kemonoArtistService.getArtistByPage(1, 10,
                    new QueryWrapper<>(new Artist().setEnable(true).setDomain(ArtistDomainEnum.FANBOX)).orderByAsc("rand()"));
            artists.addAll(artistByPage.getRecords());
        }
        if (artists.isEmpty()) {
            log.info("作者队列为空，不需要爬取");
            return null;
        }
        List<HttpRequest> httpGetRequestList = buildArtistRequest(artists);
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        return GeccoEngine.create()
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start(httpGetRequestList).pipelineFactory(springPipelineFactory).scheduler(new UniqueSpiderScheduler())
                //开启几个爬虫线程
                .thread(3)
                .retry(3)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false);
    }

    private List<HttpRequest> buildArtistRequest(List<Artist> artists) {
        List<HttpRequest> httpGetRequestList = artists.stream().map(artist -> {
            if (ArtistDomainEnum.KEMONO == artist.getDomain()) {
                return new HttpGetRequest(String.format("https://kemono.su/api/v1/%s/user/%s/posts-legacy", artist.getType(), artist.getArtistId()));
            } else if (ArtistDomainEnum.FANBOX == artist.getDomain()) {
                return FanboxHelper.getHttpRequest(String.format("https://api.fanbox.cc/post.paginateCreator?creatorId=%s", artist.getArtistId()), fanboxsessid);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return httpGetRequestList;
    }

    @Scheduled(initialDelay = 0, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void schedulerDownloadUrl() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildImageGecco();
        if (geccoEngine != null) {
            geccoEngine.run();
        }

    }

    private List<HttpRequest> buildImageRequest(List<CrawlerRequest> crawlerRequests) {
        List<HttpRequest> httpGetRequestList = crawlerRequests.stream().map(request -> {
            if (request.getUrl().contains("kemono")) {
                return new HttpGetRequest(request.getUrl());
            } else if (request.getUrl().contains("fanbox")) {
                return FanboxHelper.getHttpRequest(request.getUrl(), fanboxsessid);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return httpGetRequestList;
    }

    public GeccoEngine buildImageGecco() {
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        LambdaQueryWrapper<CrawlerRequest> queryChainWrapper = new QueryWrapper<CrawlerRequest>().orderByAsc("rand()")
                .lambda()
                .eq(CrawlerRequest::getIsDone, false)
                .le(CrawlerRequest::getErrorCount, 50);
        Page<CrawlerRequest> crawlerRequestByPage = crawlerRequestService.getPage(new Page<>(1, 20), queryChainWrapper);
        if (crawlerRequestByPage.getRecords().isEmpty()) {
            log.info("爬取队列为空，不需要爬取");
            return null;
        }
        String classpath = "com.dazo66";

        return GeccoEngine.create()
                //工程的包路径
                .classpath(classpath)
                //开始抓取的页面地址
                .start(buildImageRequest(crawlerRequestByPage.getRecords()))
                .pipelineFactory(springPipelineFactory)
                .scheduler(new UniqueSpiderScheduler())
                .debug(true)
                .proxysLoader(new Proxys() {
                    @Override
                    public HttpHost getProxy() {
                        return new HttpHost("127.0.0.1", 1080);
                    }

                    @Override
                    public boolean addProxy(String host, int port) {
                        return false;
                    }

                    @Override
                    public boolean addProxy(String host, int port, String src) {
                        return false;
                    }

                    @Override
                    public void failure(String host, int port) {

                    }

                    @Override
                    public void success(String host, int port) {

                    }
                })
                //开启几个爬虫线程
                .thread(5)
                .retry(5)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false);
    }


}

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
import com.dazo66.util.StringUtil;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.downloader.proxy.Proxys;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.geccocrawler.gecco.spider.SpiderBeanFactory;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
    @Autowired
    private SpiderBeanFactory spiderBeanFactory;

    @Value("${fanbox.save.path}")
    private String fileSavePath;

    @Autowired
    private CookieConfig cookieConfig;
    @Autowired
    private KemonoProperties kemonoProperties;

    // @Scheduled(initialDelay = 0, fixedDelay = 20, timeUnit = TimeUnit.MINUTES)
    public void fixData() {
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5); // 核心线程数，根据任务数量调整
        scheduler.setThreadNamePrefix("scheduled-task-");
        // Propagate rejection so manual task reservations can be released.
        scheduler.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        scheduler.initialize();
        return scheduler;
    }

    @Scheduled(initialDelay = 0, fixedDelay = 20, timeUnit = TimeUnit.MINUTES)
    public void autoSubKemono() {
        log.info("开始自动订阅作者！");
        List<HttpRequest> httpRequests = Lists.newArrayList(kemonoProperties.favoritesUrl()).stream().map(s -> {
            HttpGetRequest httpGetRequest = new HttpGetRequest(s);
            httpGetRequest.setCookies(cookieConfig.getCookie());
            return httpGetRequest;
        }).collect(Collectors.toList());
        GeccoEngine.create()
                .spiderBeanFactory(spiderBeanFactory)
                //开始抓取的页面地址
                .start(httpRequests)
                //开启几个爬虫线程
                .thread(1)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false)
                .debug(true)
                //阻塞方式运行
                .run();
    }

    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
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

    @Scheduled(initialDelay = 1, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
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
            // 有限选取未更新的作者
            Page<Artist> artistByPage = kemonoArtistService.getArtistByPage(1, 20, new LambdaQueryWrapper<>(new Artist()).isNull(Artist::getLastUpdate));
            artists.addAll(artistByPage.getRecords());
            Artist artist = new Artist().setEnable(true).setDomain(ArtistDomainEnum.KEMONO);
            artistByPage = kemonoArtistService.getArtistByPage(1, 50,
                    new QueryWrapper<>(artist).notIn(!artists.isEmpty(), "id", artists.stream().map(Artist::getId).collect(Collectors.toList()))
                            .orderByAsc("rand()"));
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
        return GeccoEngine.create()
                //工程的包路径
                .spiderBeanFactory(spiderBeanFactory)
                //开始抓取的页面地址
                .start(httpGetRequestList).scheduler(new UniqueSpiderScheduler())
                //开启几个爬虫线程
                .thread(6)
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
                .retry(3)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                .debug(true)
                //使用pc端userAgent
                .mobile(false);
    }

    private List<HttpRequest> buildArtistRequest(List<Artist> artists) {
        List<HttpRequest> httpGetRequestList = artists.stream().map(artist -> {
            if (ArtistDomainEnum.KEMONO == artist.getDomain()) {
                return new HttpGetRequest(kemonoProperties.absoluteUrl(
                        kemonoProperties.artistPath(artist.getType(), artist.getArtistId())));
            } else if (ArtistDomainEnum.FANBOX == artist.getDomain()) {
                return FanboxHelper.getHttpRequest(String.format("https://api.fanbox.cc/post.paginateCreator?creatorId=%s", artist.getArtistId()), cookieConfig.getFanboxsessid());
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return httpGetRequestList;
    }

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void schedulerDownloadUrl() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildImageGecco();
        if (geccoEngine != null) {
            geccoEngine.run();
        }

    }

    private List<HttpRequest> buildImageRequest(List<CrawlerRequest> crawlerRequests) {
        List<HttpRequest> httpGetRequestList = crawlerRequests.stream().map(request -> {
            if (request.getUrl().startsWith("/") || kemonoProperties.isConfiguredOrigin(request.getUrl())) {
                return new HttpGetRequest(kemonoProperties.absoluteUrl(request.getUrl()));
            } else if (request.getUrl().contains("fanbox")) {
                return FanboxHelper.getHttpRequest(request.getUrl(), cookieConfig.getFanboxsessid());
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return httpGetRequestList;
    }

    public GeccoEngine buildImageGecco() {
        LambdaQueryWrapper<CrawlerRequest> queryChainWrapper1 = new QueryWrapper<CrawlerRequest>().orderByAsc("rand()")
                .lambda()
                .eq(CrawlerRequest::getIsDone, false)
                .le(CrawlerRequest::getErrorCount, 50);
        Page<CrawlerRequest> crawlerRequestByPage1 = crawlerRequestService.getPage(new Page<>(1, 20), queryChainWrapper1);
        LambdaQueryWrapper<CrawlerRequest> queryChainWrapper2 = new QueryWrapper<CrawlerRequest>().orderByAsc("rand()")
                .lambda()
                .eq(CrawlerRequest::getIsDone, false)
                .and(wrapper -> wrapper.likeRight(CrawlerRequest::getUrl, "/")
                        .or().like(CrawlerRequest::getUrl, kemonoProperties.getBaseUrl()))
                .le(CrawlerRequest::getErrorCount, 50);

        if (!crawlerRequestByPage1.getRecords().isEmpty()) {
            queryChainWrapper2.notIn(CrawlerRequest::getId, crawlerRequestByPage1.getRecords().stream().map(CrawlerRequest::getId).collect(Collectors.toList()));
        }
        Page<CrawlerRequest> crawlerRequestByPage2 = crawlerRequestService.getPage(new Page<>(1, 50), queryChainWrapper2);

        List<CrawlerRequest> list = new ArrayList<>();
        list.addAll(crawlerRequestByPage1.getRecords());
        list.addAll(crawlerRequestByPage2.getRecords());
        if (list.isEmpty()) {
            log.info("爬取队列为空，不需要爬取");
            return null;
        }

        return GeccoEngine.create()
                .spiderBeanFactory(spiderBeanFactory)
                //开始抓取的页面地址
                .start(buildImageRequest(list))
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

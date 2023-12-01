package com.dazo66.crawler.kemono.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dazo66.config.GeccoConfig;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.entity.KemonoArtist;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.service.KemonoArtistService;
import com.dazo66.util.SpringContextUtils;
import com.dazo66.util.StringUtil;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.geccocrawler.gecco.spring.SpringPipelineFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
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
public class KemonoScheduler {

    @Autowired
    private KemonoArtistService kemonoArtistService;
    @Autowired
    private CrawlerRequestService crawlerRequestService;

    @Autowired
    private CloseableHttpClient fanboxHttpClient;

    @Value("${fanbox.save.path}")
    private String fileSavePath;

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
        LambdaQueryWrapper<KemonoArtist> query = new LambdaQueryWrapper<KemonoArtist>().isNotNull(KemonoArtist::getLastUpdate).eq(KemonoArtist::getEnable, true);
        Page<KemonoArtist> unableArtist = kemonoArtistService.getArtistByPage(1, 20000, query);
        for (KemonoArtist record : unableArtist.getRecords()) {
            if (!artistSet.contains(StringUtil.cleanSym(record.getName()))) {
                record.setEnable(false);
                //fanboxArtistService.updateByArtistId(record);
                log.info("unable artist: {}", record);
            }
        }

    }

    @Scheduled(initialDelay = 0, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void schedulerSearchNeedPost() {
        // 阻塞方式运行
        GeccoEngine geccoEngine = buildArtistGecco(null);
        if (geccoEngine != null) {
            geccoEngine.run();
        }
    }

    public GeccoEngine buildArtistGecco(List<KemonoArtist> artists) {
        if (artists == null) {
            Page<KemonoArtist> artistByPage = kemonoArtistService.getArtistByPage(1, 20,
                    new QueryWrapper<>(new KemonoArtist().setEnable(true)).orderByAsc("rand()"));
            artists = artistByPage.getRecords();
        }
        if (artists.size() == 0) {
            log.info("作者队列为空，不需要爬取");
            return null;
        }
        List<HttpRequest> httpGetRequestList =
                artists.stream().map(artist -> new HttpGetRequest(String.format("https://kemono.su/%s/user/%s", artist.getType(), artist.getArtistId()))).collect(Collectors.toList());
        SpringPipelineFactory springPipelineFactory =
                SpringContextUtils.getBean(SpringPipelineFactory.class);
        return GeccoEngine.create()
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start(httpGetRequestList).pipelineFactory(springPipelineFactory).scheduler(new UniqueSpiderScheduler())
                //开启几个爬虫线程
                .thread(2)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false);
    }

    @Scheduled(initialDelay = 0, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
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
                .thread(2)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false);
    }


}

package com.dazo66.test;

import com.dazo66.util.FanboxHelper;
import com.dazo66.util.SpringContextUtils;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.downloader.proxy.Proxys;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.geccocrawler.gecco.spring.SpringPipelineFactory;
import com.google.common.collect.Lists;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:58
 **/
@SpringBootTest
public class TestSpringGeccoFanbox {

    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;

    @Test
    public void test() {
        SpringPipelineFactory springPipelineFactory = SpringContextUtils.getBean(SpringPipelineFactory.class);

        List<HttpRequest> httpRequests = Lists.newArrayList(
                "https://api.fanbox.cc/post.info?postId=6005343"
        ).stream().map(url -> FanboxHelper.getHttpRequest(url, fanboxsessid)).collect(Collectors.toList());

        UniqueSpiderScheduler scheduler = new UniqueSpiderScheduler();
        GeccoEngine.create()
                // post__attachment-link
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start(httpRequests)
                .pipelineFactory(springPipelineFactory)
                .scheduler(scheduler)
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
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                .retry(10)
                //使用pc端userAgent
                .mobile(false)
                //阻塞方式运行
                .run();
    }

}

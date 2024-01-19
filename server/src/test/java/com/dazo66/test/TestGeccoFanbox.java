package com.dazo66.test;

import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.downloader.proxy.Proxys;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.google.common.collect.Lists;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dazo66(sundazhong.sdz)
 * @date 2023/11/5 12:58
 **/
public class TestGeccoFanbox {

    private String sessId = "21689594";

    @Test
    public void test() {


        List<HttpRequest> httpRequests = Lists.newArrayList(
                "https://api.fanbox.cc/post.info?postId=7309268",
                "https://api.fanbox.cc/post.info?postId=7310418",
                "https://api.fanbox.cc/post.listCreator?creatorId=niki-niki&limit=5"
        ).stream().map(this::getHttpRequest).collect(Collectors.toList());

        UniqueSpiderScheduler scheduler = new UniqueSpiderScheduler();
        GeccoEngine.create()
                // post__attachment-link
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start(httpRequests)
                .pipelineFactory(new TestPipelineFactory())
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
                //使用pc端userAgent
                .mobile(false)
                //阻塞方式运行
                .run();
    }

    private HttpRequest getHttpRequest(String url) {
        HttpRequest httpRequest = new HttpGetRequest(url);
        httpRequest.addCookie("FANBOXSESSID", sessId);
        httpRequest.addHeader("Referer", "https://www.fanbox.cc/");
        httpRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36");
        httpRequest.addHeader("Origin", "https://www.fanbox.cc");
        return httpRequest;
    }

    public void initPipeline() {

    }

}

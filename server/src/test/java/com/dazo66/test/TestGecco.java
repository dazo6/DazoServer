package com.dazo66.test;

import com.dazo66.crawler.kemono.pipeline.KemonoImagePipeline;
import com.dazo66.service.ConsolePipeline;
import com.dazo66.util.ImageUtils;
import com.dazo66.util.SpringContextUtils;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.downloader.proxy.Proxys;
import com.geccocrawler.gecco.request.HttpGetRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import com.geccocrawler.gecco.spring.SpringPipelineFactory;
import com.google.common.collect.Lists;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.AntPathMatcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("test")
public class TestGecco {

    @Autowired
    private SpringPipelineFactory springPipelineFactory;

    @Test
    public void thumbnailator() throws IOException {
        File file = new File("F:\\DazoServer\\test\\img");
        File[] testImgs = file.listFiles();
        for (int i = 0; i < testImgs.length; i++) {
            for (int j = 0; j < 4; j++) {
                double q = 1d - 0.1 * j;
                File file1 = new File(testImgs[i].getParent() + "/" + i + "-" + q + ".jpg");
                ImageUtils.zipImageFile(ImageIO.read(new FileInputStream(testImgs[i])), file1, q);
            }
        }
    }

    @Test
    public void testNeed() throws IOException {
        File file = new File("F:\\DazoServer\\test\\img");
        File[] testImgs = file.listFiles();
        for (int i = 0; i < testImgs.length; i++) {
            File testImg = testImgs[i];
            BufferedImage image = ImageIO.read(new FileInputStream(testImg));
            System.out.println(testImg.getName() + " : " + ImageUtils.needCompression(image,
                    ((int) testImg.length())));
        }
    }

    @Test
    public void testUser() {
        System.out.println(ThumbnailatorUtils.getSupportedOutputFormats());
        GeccoEngine.create()
                // post__attachment-link
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start("https://kemono.su/fanbox/user/79281725").pipelineFactory(name -> {
            if (name.equals("consolePipeline")) {
                return new ConsolePipeline();
            }
            return null;
        }).scheduler(new UniqueSpiderScheduler())
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

    @Test
    public void test() {
/*        testOne("https://kemono.su/fanbox/user/18209583/post/4646634");
        testOne("https://kemono.su/fanbox/user/4606746/post/7262836");
        testOne("https://kemono.su/fanbox/user/26654959/post/7225710");*/
        testOne("https://kemono.su/fanbox/user/55386946/post/6402569");
    }

    @Test
    public void getDataStr() {
        System.out.println(KemonoImagePipeline.getDataStr("<div style=\\\"width: 89px; display: inline-block;\\\">\\n Published:\\n</div> 2023-07-27"));
    }

    public void testOne(String url) {


        List<HttpRequest> httpRequests = Lists.newArrayList(url).stream().map(HttpGetRequest::new).collect(Collectors.toList());

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
                //使用pc端userAgent
                .mobile(false)
                //阻塞方式运行
                .run();
    }

    @Test
    public void testFav() {
        System.out.println(ThumbnailatorUtils.getSupportedOutputFormats());
        UniqueSpiderScheduler scheduler = new UniqueSpiderScheduler();
        SpringPipelineFactory springPipelineFactory = SpringContextUtils.getBean(SpringPipelineFactory.class);
        GeccoEngine.create()
                // post__attachment-link
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start("https://kemono.su/favorites").pipelineFactory(springPipelineFactory).scheduler(scheduler)
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

    @Test
    public void antPathTest() {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        System.out.println(antPathMatcher.match("/**/get*", "/dashboard/getCategoryList"));
    }

}

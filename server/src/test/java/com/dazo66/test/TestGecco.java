package com.dazo66.test;

import com.dazo66.service.ConsolePipeline;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;

public class TestGecco {

    public static void main(String[] args) {
        GeccoEngine.create()
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start("https://kemono.party/gumroad/user/6336712228921/post/KrEnkx").pipelineFactory(name -> {
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

}

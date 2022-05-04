package com.dazo66.test;

public class FanboxDownloadTest {

    public static void main(String[] args) {
        System.out.println("jpeg".replaceAll("jpe$", "jpg"));
        /*GeccoEngine.create()
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start("https://kemono.party/fanbox/user/4606746")
                .pipelineFactory(name -> {
                    if ("fanboxImagePipeline".equals(name)) {
                        return new FanboxImagePipeline();
                    } else if ("fanboxPostPipeline".equals(name)) {
                        return new FanboxPostPipeline();
                    }
                    return null;
                })
                .scheduler(new UniqueSpiderScheduler())
                //开启几个爬虫线程
                .thread(1)
                //单个爬虫每次抓取完一个请求后的间隔时间
                .interval(2000)
                //循环抓取
                .loop(false)
                //使用pc端userAgent
                .mobile(false)
                //非阻塞方式运行
                .start();*/
    }

}

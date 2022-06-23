package com.dazo66.test;

import com.dazo66.service.ConsolePipeline;
import com.dazo66.util.ImageUtils;
import com.geccocrawler.gecco.GeccoEngine;
import com.geccocrawler.gecco.scheduler.UniqueSpiderScheduler;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TestGecco {

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
    public void test() {
        System.out.println(ThumbnailatorUtils.getSupportedOutputFormats());
        GeccoEngine.create()
                // post__attachment-link
                //工程的包路径
                .classpath("com.dazo66")
                //开始抓取的页面地址
                .start("https://kemono.party/gumroad/user/2023024895565/post/CWRNp").pipelineFactory(name -> {
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

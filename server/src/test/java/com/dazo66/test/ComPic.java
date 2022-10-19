package com.dazo66.test;

import com.dazo66.util.ImageUtils;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ComPic {


    public static void main(String[] args) throws IOException, InterruptedException {


        Collection<File> files = FileUtils.listFiles(new File("Z:\\OneDrive\\色图\\kemono"),
                new String[]{"png"}, true);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch countDownLatch = new CountDownLatch(files.size());
        for (File file : files) {
            executorService.submit(() -> {
                try {
                    FileInputStream input = new FileInputStream(file);
                    BufferedImage image = ImageIO.read(input);
                    double quality = 1d;
                    if (ImageUtils.needCompression(image, file.length())) {
                        quality = 0.9d;
                    }
                    ImageUtils.zipImageFile(image, new File(file.getAbsolutePath().substring(0,
                            file.getAbsolutePath().length() - 4)), quality);
                    input.close();
                    file.delete();
                    countDownLatch.countDown();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        while (true) {
            Thread.sleep(1000L);
            System.out.println(countDownLatch.getCount() / files.size());
        }
    }

}

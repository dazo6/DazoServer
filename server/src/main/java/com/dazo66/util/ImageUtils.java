package com.dazo66.util;

import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageUtils {

    public static void zipImageFile(BufferedImage image, File desFile, double quality) throws IOException {
        File parentFile = desFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        Thumbnails.of(image).scale(1d).outputFormat("jpg").outputQuality(quality).toFile(desFile);
    }

    public static boolean needCompression(BufferedImage image, int byteLen) {
        return byteLen > 2000 * 1000 || byteLen > image.getWidth() * image.getHeight() / 2;
    }

}

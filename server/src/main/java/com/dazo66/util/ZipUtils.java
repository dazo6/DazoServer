package com.dazo66.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.function.BiFunction;

/**
 * @Description 解压缩文件工具类
 * @Author Mr.nobody
 * @Date 2021/3/8
 * @Version 1.0.0
 */
public class ZipUtils {

    /**
     * 解压
     *
     * @param zipFilePath 带解压文件
     * @throws Exception
     */
    public static void unzip(String zipFilePath, BiFunction<ZipEntry, Integer, String> fileNameFunction) throws Exception {
        // 读入流
        ZipFile zipInputStream = new ZipFile(new File(zipFilePath));
        // 遍历每一个文件
        Enumeration<ZipEntry> entries = zipInputStream.getEntries();
        Integer index = 0;
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            String fileName = fileNameFunction.apply(zipEntry, index);
            if (zipEntry.isDirectory()) { // 文件夹
                // 直接创建
                if (!StringUtils.isEmpty(fileName)) {
                    mkdir(new File(fileName));
                }
            } else { // 文件
                File file = new File(fileName);
                // 创建父目录
                mkdir(file.getParentFile());
                // 写出文件流
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(Files.newOutputStream(file.toPath()));
                byte[] bytes = new byte[1024];
                int readLen;
                InputStream inputStream = zipInputStream.getInputStream(zipEntry);
                while ((readLen = inputStream.read(bytes)) != -1) {
                    bufferedOutputStream.write(bytes, 0, readLen);
                }
                inputStream.close();
                bufferedOutputStream.close();
            }
            index++;
        }
        zipInputStream.close();
    }

    // 如果父目录不存在则创建
    private static void mkdir(File file) {
        if (null == file || file.exists()) {
            return;
        }
        mkdir(file.getParentFile());
        file.mkdir();
    }

    public static void main(String[] args) throws Exception {
        String zipFilePath = "D:\\DazoServer\\test\\test.zip";
        unzip(zipFilePath, new BiFunction<ZipEntry, Integer, String>() {
            @Override
            public String apply(ZipEntry zipEntry, Integer integer) {
                if (zipEntry.isDirectory()) {
                    return "";
                }
                return "D:\\DazoServer\\test\\test" + integer + zipEntry.getName();
            }
        });
    }
}
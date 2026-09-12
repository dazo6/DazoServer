package com.dazo66.qqbot.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 媒体文件下载工具。
 *
 * @author Dazo66
 */
@Slf4j
public class MediaDownloadUtils {

    /**
     * 从 URL 下载文件到本地目录。
     *
     * @param fileUrl  文件 URL
     * @param saveDir  保存目录
     * @param fileName 文件名（不含路径）
     * @return 下载成功返回文件绝对路径，失败返回 null
     */
    public static String download(String fileUrl, String saveDir, String fileName) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return null;
        }

        File dir = new File(saveDir);
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("创建下载目录失败: {}", saveDir);
            return null;
        }

        File targetFile = new File(dir, fileName);
        HttpURLConnection conn = null;

        try {
            URL url = new URL(fileUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int respCode = conn.getResponseCode();
            if (respCode != HttpURLConnection.HTTP_OK) {
                log.warn("下载文件失败, HTTP {}: {}", respCode, fileUrl);
                return null;
            }

            try (InputStream is = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            log.debug("文件下载成功: {}", targetFile.getAbsolutePath());
            return targetFile.getAbsolutePath();

        } catch (Exception e) {
            log.error("下载文件异常: {}", fileUrl, e);
            // 下载失败则删除不完整的文件
            if (targetFile.exists()) {
                targetFile.delete();
            }
            return null;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 从文件名推断扩展名。
     */
    public static String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}

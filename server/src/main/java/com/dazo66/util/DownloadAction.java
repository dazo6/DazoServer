package com.dazo66.util;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.thymeleaf.util.MapUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

@Slf4j
public class DownloadAction implements Callable<Boolean> {

    private Boolean compression;
    private String url;
    private String localPath;
    private OkHttpClient httpClient;
    private int retryCount = 10;

    private Map<String, String> headers;

    private Map<String, String> cookie;

    private Boolean useMultiDownloader = true;

    public DownloadAction(String url, String localPath, Boolean compression) {
        this.url = url;
        this.localPath = localPath;
        this.compression = compression;
        OkHttpClient bean = SpringContextUtils.getBean(OkHttpClient.class);
        if (bean != null) {
            this.httpClient = bean;
        } else {
            httpClient = new OkHttpClient();
        }
    }

    public void setUseMultiDownloader(Boolean useMultiDownloader) {
        this.useMultiDownloader = useMultiDownloader;
    }

    public void setCompression(Boolean compression) {
        this.compression = compression;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setCookie(Map<String, String> cookie) {
        this.cookie = cookie;
    }



    @Override
    public Boolean call() {
        Request.Builder builder = new Request.Builder().url(url);
        if (!MapUtils.isEmpty(headers)) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        if (!MapUtils.isEmpty(cookie)) {
            StringBuilder cookieBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : cookie.entrySet()) {
                cookieBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
            }
            builder.addHeader("Cookie", cookieBuilder.toString());
        }
        Response response = null;
        File file = null;
        InputStream inputStream = null;
        try {
            response = httpClient.newCall(builder.build()).execute();
            if (response.isSuccessful()) {
                String remoteFilename = url.substring(url.lastIndexOf("/") - 1);
                String suffix = remoteFilename.substring(remoteFilename.lastIndexOf('.')).replaceAll("jpe$", "jpg");
                String filename = localPath + suffix;
                file = new File(filename);
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                String fileType = suffix.substring(1);
                if (ThumbnailatorUtils.getSupportedOutputFormats().contains(fileType) && !"gif".equalsIgnoreCase(fileType) || !useMultiDownloader) {
                    file = new File(localPath + "." + fileType);
                    // 文件已存在 且有内容 就跳过
                    if (file.exists() && file.length() > 1024) {
                        tryUnizp(fileType, file);
                        return true;
                    }
                    long contentLength = response.headers("Content-Length").isEmpty() ? 0 : Long.parseLong(response.headers("Content-Length").get(0));
                    double quality = 1d;
                    inputStream = response.body().byteStream();
                    if ((compression == null || compression) && contentLength > 0 && ThumbnailatorUtils.getSupportedOutputFormats().contains(fileType) && !"gif".equalsIgnoreCase(fileType)) {
                        BufferedImage image = ImageIO.read(inputStream);
                        if (ImageUtils.needCompression(image, contentLength)) {
                            quality = 0.9d;
                        }
                        ImageUtils.zipImageFile(image, new File(localPath), quality);
                    } else {
                        OutputStream output = Files.newOutputStream(file.toPath(), CREATE_NEW);
                        IOUtils.copy(inputStream, output);
                        output.close();
                    }
                    log.info("download and compression success name: {}.{}, quality: {}", localPath, fileType, quality);
                } else {
                    MultiThreadHttpDownloader multiThreadHttpDownloader = new MultiThreadHttpDownloader(httpClient, url, file.getAbsolutePath(), 8, 5000);
                    multiThreadHttpDownloader.setHeaders(headers);
                    multiThreadHttpDownloader.setCookie(cookie);
                    multiThreadHttpDownloader.get();
                    log.info("MultiThreadHttpDownloader download success " + filename);
                }
                tryUnizp(fileType, file);
                return true;
            } else {
                log.error("download error, " + response.code() + " url: "
                        + url);
                retryCount--;
                if (retryCount > 0) {
                    return call();
                } else {
                    return false;
                }
            }
        } catch (Throwable e) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
            if (file != null && file.exists()) {
                file.delete();
            }
            log.error("download error {}, {}", url, ExceptionUtils.getFullStackTrace(e));
            retryCount--;
            if (retryCount > 0) {
                return call();
            } else {
                return false;
            }
        } finally {
            if (response != null) {

                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ex) {
                        // ignore
                    }
                }

            }
        }
    }

    private void tryUnizp(String fileType, File file) throws Exception {
        if (fileType.equals("zip") || fileType.equals("rar") || fileType.equals("7z")) {
            ZipUtils.unzip(file.getAbsolutePath(), (zipEntry, integer) -> {
                if (zipEntry.isDirectory()) {
                    return "";
                }
                String name = zipEntry.getName();
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf('/') + 1);
                }
                return localPath + "-" + String.format("%04d", integer) + "-" + name;

            });
        }
    }

}

package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.thymeleaf.util.MapUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
    private CloseableHttpClient httpClient;
    private int retryCount = 10;

    private Map<String, String> headers;

    private Map<String, String> cookie;

    public DownloadAction(CloseableHttpClient client, String url, String localPath, Boolean compression) {
        this.url = url;
        this.localPath = localPath;
        this.httpClient = client;
        this.compression = compression;
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
        HttpClientContext cookieContext = HttpClientContext.create();
        cookieContext.setCookieStore(new BasicCookieStore());
        HttpRequestBase request = new HttpGet(url);
        if (!MapUtils.isEmpty(headers)) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }
        if (!MapUtils.isEmpty(cookie)) {
            for (Map.Entry<String, String> entry : cookie.entrySet()) {
                BasicClientCookie cookie = new BasicClientCookie(entry.getKey(), entry.getValue());
                cookie.setPath("/");
                cookie.setDomain(request.getURI().getHost());
                cookieContext.getCookieStore().addCookie(cookie);
            }
        }
        CloseableHttpResponse response = null;
        File file = null;
        try {
            response = httpClient.execute(request, cookieContext);
            while (response.getStatusLine().getStatusCode() == 302) {
                HeaderElement[] locations = response.getFirstHeader("Location").getElements();
                if (locations != null && locations.length != 0) {
                    response.close();
                    request = new HttpGet(locations[0].toString());
                    response = httpClient.execute(request, cookieContext);
                }
            }
            if (response.getStatusLine().getStatusCode() == 200) {
                Header[] responseHeaders = response.getHeaders("content-type");
                String remoteFilename;
                if (responseHeaders.length == 0 || responseHeaders[0].getElements().length == 0 || responseHeaders[0].getElements()[0].getParameters().length == 0 || url.contains("fanbox.cc")) {
                    remoteFilename = url.substring(url.lastIndexOf("/") - 1);
                } else {
                    remoteFilename = responseHeaders[0].getElements()[0].getParameter(0).getValue();
                }
                String suffix = remoteFilename.substring(remoteFilename.lastIndexOf('.')).replaceAll(
                                "jpe$", "jpg");
                String filename = localPath + suffix;
                file = new File(filename);
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                String fileType = suffix.substring(1);
                if (ThumbnailatorUtils.getSupportedOutputFormats().contains(fileType) && !"gif".equalsIgnoreCase(fileType)) {
                    file = new File(localPath + ".jpg");
                    if (file.exists()) {
                        return true;
                    }
                    Long contentLength = response.getFirstHeader("content-length") == null ? 0 : Long.parseLong(response.getFirstHeader("content-length").getValue());
                    double quality = 1d;
                    if ((compression == null || compression) && contentLength > 0) {
                        BufferedImage image = ImageIO.read(response.getEntity().getContent());
                        if (ImageUtils.needCompression(image, contentLength)) {
                            quality = 0.9d;
                        }
                        ImageUtils.zipImageFile(image, new File(localPath), quality);
                    } else {
                        OutputStream output = Files.newOutputStream(file.toPath(), CREATE_NEW);
                        IOUtils.copy(response.getEntity().getContent(), output);
                        output.close();
                    }
                    log.info("download and compression success name: {}.{}, quality: {}", localPath, fileType, quality);
                } else {
                    response.close();
                    MultiThreadHttpDownloader multiThreadHttpDownloader = new MultiThreadHttpDownloader(httpClient, url, file.getAbsolutePath(), 8, 5000);
                    multiThreadHttpDownloader.setHeaders(headers);
                    multiThreadHttpDownloader.setCookie(cookie);
                    multiThreadHttpDownloader.get();
                    log.info("download success " + filename);
                }
                return true;
            } else {
                log.error("download error, " + response.getStatusLine().getStatusCode() + " url: "
                        + url);
                response.close();
                retryCount--;
                if (retryCount > 0) {
                    return call();
                } else {
                    return false;
                }
            }
        } catch (Throwable e) {
            try {
                Thread.sleep(1000L);
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
                try {
                    response.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

}

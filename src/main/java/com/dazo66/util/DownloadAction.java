package com.dazo66.util;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

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

    private static final int MAX_RETRY_COUNT = 3;
    private static final long REQUEST_INTERVAL_MILLIS = 2000L;
    private static final long DEFAULT_429_BACKOFF_MILLIS = 60_000L;
    private static final long MAX_429_BACKOFF_MILLIS = 10 * 60_000L;
    private static final int RESPONSE_LOG_LIMIT = 2000;
    private static final Object REQUEST_RATE_LOCK = new Object();
    private static long nextRequestAt;

    private Boolean compression;
    private String url;
    private String localPath;
    private OkHttpClient httpClient;
    private int retryCount = MAX_RETRY_COUNT;

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
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        if (cookie != null && !cookie.isEmpty()) {
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
            awaitRequestSlot();
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
                        try {
                            ImageUtils.zipImageFile(image, new File(localPath), quality);
                        } catch (Exception e) {
                            log.error("解压异常 可能是加密了：" + localPath);
                        }
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
                int status = response.code();
                String responseBody = responseBodyPreview(response);
                log.error("download error, status: {}, url: {}, response body: {}",
                        status, url, responseBody);

                // 403 is an explicit server-side denial. Retrying it only increases
                // the chance of extending the IP ban/rate limit.
                if (status == 403) {
                    return false;
                }

                closeResponseBody(response);
                retryCount--;
                if (retryCount > 0) {
                    if (status == 429 && !waitBeforeRetry(response)) {
                        return false;
                    }
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
            if (file != null && file.exists()) {
                file.delete();
            }
            log.error("download error {}, {}", url, ExceptionUtils.getStackTrace(e));
            retryCount--;
            if (retryCount > 0) {
                return call();
            } else {
                return false;
            }
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
            closeResponseBody(response);
        }
    }

    private void closeResponseBody(Response response) {
        if (response != null && response.body() != null) {
            try {
                response.body().close();
            } catch (IOException ex) {
                log.debug("关闭下载响应体失败", ex);
            }
        }
    }

    /**
     * Reserve the next request start time globally so multiple download worker
     * threads cannot create a burst against the same remote host/proxy exit IP.
     */
    private void awaitRequestSlot() throws InterruptedException {
        long waitMillis;
        synchronized (REQUEST_RATE_LOCK) {
            long now = System.currentTimeMillis();
            long scheduledAt = Math.max(now, nextRequestAt);
            nextRequestAt = scheduledAt + REQUEST_INTERVAL_MILLIS;
            waitMillis = scheduledAt - now;
        }
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    private String responseBodyPreview(Response response) {
        if (response == null || response.body() == null) {
            return "<empty>";
        }
        try {
            String body = response.body().string();
            if (body == null || body.isEmpty()) {
                return "<empty>";
            }
            return body.length() <= RESPONSE_LOG_LIMIT
                    ? body : body.substring(0, RESPONSE_LOG_LIMIT) + "...";
        } catch (IOException e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }

    private boolean waitBeforeRetry(Response response) {
        long delayMillis = retryAfterMillis(response);
        log.warn("HTTP 429 from {}, waiting {} ms before retry", url, delayMillis);
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private long retryAfterMillis(Response response) {
        long delayMillis = DEFAULT_429_BACKOFF_MILLIS;
        String retryAfter = response == null ? null : response.header("Retry-After");
        if (retryAfter != null) {
            try {
                delayMillis = Long.parseLong(retryAfter.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // HTTP-date form is not needed for this server; use the safe default.
            }
        }
        int retryIndex = MAX_RETRY_COUNT - retryCount;
        delayMillis = Math.max(delayMillis, DEFAULT_429_BACKOFF_MILLIS * Math.max(1, retryIndex));
        return Math.min(delayMillis, MAX_429_BACKOFF_MILLIS);
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

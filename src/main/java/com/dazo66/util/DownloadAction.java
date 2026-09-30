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
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

@Slf4j
public class DownloadAction implements Callable<Boolean> {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long DEFAULT_429_BACKOFF_MILLIS = 60_000L;
    private static final long MAX_429_BACKOFF_MILLIS = 10 * 60_000L;
    private static final int RESPONSE_LOG_LIMIT = 2000;

    private Boolean compression;
    private String url;
    private String localPath;
    private OkHttpClient httpClient;
    private int retryCount = MAX_RETRY_COUNT;

    private Map<String, String> headers;

    private Map<String, String> cookie;

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
        // Kept for source compatibility. Downloads are intentionally single
        // stream now so callers cannot exceed the remote active-download cap.
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
        String sourceSuffix = fileSuffix(url);
        String sourceFileType = sourceSuffix.substring(1);
        boolean convertToJpeg = (compression == null || compression)
                && ThumbnailatorUtils.getSupportedOutputFormats().contains(sourceFileType)
                && !"gif".equalsIgnoreCase(sourceFileType);
        String targetSuffix = convertToJpeg ? ".jpg" : sourceSuffix;
        String targetFileType = targetSuffix.substring(1);
        File file = new File(localPath + targetSuffix);
        if (file.exists() && file.length() > 1024) {
            try {
                tryUnizp(targetFileType, file);
                return true;
            } catch (Exception e) {
                log.error("解压已下载文件失败: {}", file.getAbsolutePath(), e);
                return false;
            }
        }

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
        InputStream inputStream = null;
        try (RemoteRequestGate.Permit requestPermit = RemoteRequestGate.KEMONO_GATE.acquire()) {

            response = httpClient.newCall(builder.build()).execute();
            if (response.isSuccessful()) {
                if (file.getParentFile() != null && !file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                // Always consume the original response as one download. Starting
                // range workers here used to create 8 additional requests and
                // violates the remote service's active-download limit.
                long contentLength = response.headers("Content-Length").isEmpty() ? 0 : Long.parseLong(response.headers("Content-Length").get(0));
                double quality = 1d;
                inputStream = response.body().byteStream();
                if (convertToJpeg) {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image == null) {
                        throw new IOException("response is not a decodable image: " + url);
                    }
                    if (contentLength > 0 && ImageUtils.needCompression(image, contentLength)) {
                        quality = 0.9d;
                    }
                    try {
                        ImageUtils.zipImageFile(image, file, quality);
                    } catch (Exception e) {
                        log.error("解压异常 可能是加密了：" + localPath);
                        throw e;
                    }
                } else {
                    try (OutputStream output = Files.newOutputStream(file.toPath(), CREATE, TRUNCATE_EXISTING)) {
                        IOUtils.copy(inputStream, output);
                    }
                }
                log.info("download and compression success name: {}.{}, quality: {}", localPath, targetFileType, quality);
                tryUnizp(targetFileType, file);
                return true;
            } else {
                int status = response.code();
                String responseBody = responseBodyPreview(response);
                log.error("download error, status: {}, url: {}, response body: {}",
                        status, url, responseBody);

                if (status == 403) {
                    return false;
                }

                long retryDelayMillis = status == 429 ? retryAfterMillis(response) : 0L;
                closeResponseBody(response);
                response = null;
                retryCount--;
                if (retryCount > 0) {
                    if (status == 429 && !waitBeforeRetry(retryDelayMillis)) {
                        return false;
                    }
                    return call();
                } else {
                    return false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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
                closeResponseBody(response);
                response = null;
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

    private boolean waitBeforeRetry(long delayMillis) {
        log.warn("HTTP 429 from {}, waiting {} ms before retry", url, delayMillis);
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isRateLimitResponse(String responseBody) {
        if (responseBody == null) {
            return false;
        }
        String normalized = responseBody.toLowerCase();
        return normalized.contains("are you a bot")
                || normalized.contains("reasonable rate limits")
                || normalized.contains("active downloads");
    }

    private String fileSuffix(String sourceUrl) {
        String path = URI.create(sourceUrl).getPath();
        int slash = path == null ? -1 : path.lastIndexOf('/');
        int dot = path == null ? -1 : path.lastIndexOf('.');
        if (dot <= slash || dot == path.length() - 1) {
            throw new IllegalArgumentException("download URL has no file extension: " + sourceUrl);
        }
        String suffix = path.substring(dot).toLowerCase();
        return ".jpe".equals(suffix) ? ".jpg" : suffix;
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

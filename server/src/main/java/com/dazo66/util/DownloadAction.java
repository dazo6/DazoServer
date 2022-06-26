package com.dazo66.util;

import com.dazo66.config.FanboxHttpClientConfig;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.apache.http.HeaderElement;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@Slf4j
public class DownloadAction implements Callable<Boolean> {

    private String url;
    private String localPath;
    private CloseableHttpClient httpClient;
    private int retryCount = 10;

    public DownloadAction(CloseableHttpClient client, String url, String localPath) {
        this.url = url;
        this.localPath = localPath;
        this.httpClient = client;
    }

    @Override
    public Boolean call() {
        HttpRequestBase request = new HttpGet(url);
        CloseableHttpResponse response = null;
        File file = null;
        try {
            response = httpClient.execute(request);
            while (response.getStatusLine().getStatusCode() == 302) {
                HeaderElement[] locations = response.getFirstHeader("Location").getElements();
                if (locations != null && locations.length != 0) {
                    response.close();
                    request = new HttpGet(locations[0].toString());
                    response = httpClient.execute(request);
                }
            }
            if (response.getStatusLine().getStatusCode() == 200) {
                String remoteFilename =
                        response.getHeaders("Content-Disposition")[0].getElements()[0].getParameter(0).getValue();
                String suffix =
                        remoteFilename.substring(remoteFilename.lastIndexOf('.')).replaceAll(
                                "jpe$", "jpg");
                String filename = localPath + suffix;
                file = new File(filename);
                String fileType = suffix.substring(1);
                if (ThumbnailatorUtils.getSupportedOutputFormats().contains(fileType) && !"gif".equalsIgnoreCase(fileType)) {
                    file = new File(localPath + ".jpg");
                    final long contentLength = Long.parseLong(response.getFirstHeader("content" +
                            "-length").getValue());
                    BufferedImage image = ImageIO.read(response.getEntity().getContent());
                    double quality = 1d;
                    if (ImageUtils.needCompression(image, contentLength)) {
                        quality = 0.9d;
                    }
                    ImageUtils.zipImageFile(image, new File(localPath), quality);
                    log.info("download and compression success name: {}.{}, quality: {}",
                            localPath, fileType, quality);
                } else {
                    response.close();
                    new MultiThreadHttpDownloader(url, file.getAbsolutePath(), 5, 5000,
                            FanboxHttpClientConfig.proxy).get();
                    log.info("download success " + filename);
                }
                return true;
            } else {
                log.error("download error, " + response.getStatusLine().getStatusCode());
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
            log.error("download error ", e);
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

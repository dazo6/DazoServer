package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.util.ThumbnailatorUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HeaderElement;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.Callable;

@Slf4j
public class DownloadAction implements Callable<Boolean> {

    private String url;
    private String localPath;
    private HttpClient httpClient;
    private int retryCount = 10;

    public DownloadAction(HttpClient client, String url, String localPath) {
        this.url = url;
        this.localPath = localPath;
        this.httpClient = client;
    }

    @Override
    public Boolean call() {
        HttpRequestBase request = new HttpGet(url);
        File file = null;
        try {
            org.apache.http.HttpResponse response = httpClient.execute(request);
            while (response.getStatusLine().getStatusCode() == 302) {
                HeaderElement[] locations = response.getFirstHeader("Location").getElements();
                if (locations != null && locations.length != 0) {
                    request.releaseConnection();
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
                if ((".psd".equalsIgnoreCase(suffix)) || (file.exists() && file.isFile())) {
                    log.info("ignore " + filename);
                    return true;
                }
                if (ThumbnailatorUtils.getSupportedOutputFormats().contains(suffix.substring(1))) {
                    byte[] bytes = IOUtils.toByteArray(response.getEntity().getContent());
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                    double quality = 1d;
                    if (ImageUtils.needCompression(image, bytes.length)) {
                        quality = 0.9d;
                    }
                    ImageUtils.zipImageFile(image, new File(localPath), quality);
                    log.info("download and compression success name: {}.jpg, quality: {}",
                            localPath, quality);
                } else {
                    log.info("download success " + filename);
                    FileUtils.copyInputStreamToFile(response.getEntity().getContent(), file);
                }
                return true;
            } else {
                log.error("download error, " + response.getStatusLine().getStatusCode());
                retryCount--;
                if (retryCount > 0) {
                    return call();
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            if (file != null && file.exists()) {
                file.delete();
            }
            log.error("download error,  " + e.toString());
            retryCount--;
            if (retryCount > 0) {
                return call();
            } else {
                return false;
            }
        } finally {
            request.releaseConnection();
        }
    }

}

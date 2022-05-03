package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.HeaderElement;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

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
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36");
        request.setHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9," +
                "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0" + ".9");
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
                String filename =
                        localPath + remoteFilename.substring(remoteFilename.lastIndexOf('.'));
                FileUtils.copyInputStreamToFile(response.getEntity().getContent(),
                        new File(filename));
                log.info("download success " + filename);
                return true;
            } else {
                retryCount--;
                if (retryCount > 0) {
                    return call();
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
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

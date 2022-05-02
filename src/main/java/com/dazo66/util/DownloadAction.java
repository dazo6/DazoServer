package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.HeaderElement;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.File;

@Slf4j
public class DownloadAction implements Runnable {

    private String url;
    private String localPath;
    private HttpClient httpClient;
    private int retryCount = 5;

    public DownloadAction(HttpClient client, String url, String localPath) {
        this.url = url;
        this.localPath = localPath;
        this.httpClient = client;
    }

    @Override
    public void run() {
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
            FileUtils.copyInputStreamToFile(response.getEntity().getContent(), new File(localPath));
            log.info("download success " + localPath);
        } catch (Exception e) {
            log.error("Download error,  " + e.toString());
            retryCount--;
            if (retryCount > 0) {
                run();
            }
        } finally {
            request.releaseConnection();
        }
    }

}

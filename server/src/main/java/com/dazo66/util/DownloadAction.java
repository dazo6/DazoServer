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
                        localPath + remoteFilename.substring(remoteFilename.lastIndexOf('.')).replaceAll("jpe$", "jpg");
                FileUtils.copyInputStreamToFile(response.getEntity().getContent(),
                        new File(filename));
                log.info("download success " + filename);
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

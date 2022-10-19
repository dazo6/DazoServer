package com.dazo66.util;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dazo66
 */
@Slf4j
public class MultiThreadHttpDownloader {

    private final URL url;
    private final File localFile;
    private final Object waiting = new Object();
    private final AtomicInteger downloadedBytes = new AtomicInteger(0);
    private final AtomicInteger aliveThreads = new AtomicInteger(0);
    private final int MIN_SIZE = 2 << 20;
    private final CloseableHttpClient httpClient;
    private boolean multiThreaded = true;
    private int fileSize = 0;
    private int THREAD_NUM = 5;
    private int TIME_OUT = 5000;

    public MultiThreadHttpDownloader(CloseableHttpClient httpClient, String url,
                                     String localPath) throws MalformedURLException {
        this.httpClient = httpClient;
        this.url = new URL(url);
        this.localFile = new File(localPath);
    }

    public MultiThreadHttpDownloader(CloseableHttpClient httpClient, String url, String localPath
            , int threadNum, int timeout) throws MalformedURLException {
        this(httpClient, url, localPath);
        this.THREAD_NUM = threadNum;
        this.TIME_OUT = timeout;
    }

    //开始下载文件
    public void get() throws IOException {
        long startTime = System.currentTimeMillis();

        boolean resumable = supportResumeDownload();
        if (!resumable || THREAD_NUM == 1 || fileSize < MIN_SIZE) {
            multiThreaded = false;
        }
        if (!multiThreaded) {
            new DownloadThread(0, 0, fileSize - 1).start();
            ;
        } else {
            int[] endPoint = new int[THREAD_NUM + 1];
            int block = fileSize / THREAD_NUM;
            for (int i = 0; i < THREAD_NUM; i++) {
                endPoint[i] = block * i;
            }
            endPoint[THREAD_NUM] = fileSize;
            for (int i = 0; i < THREAD_NUM; i++) {
                new DownloadThread(i, endPoint[i], endPoint[i + 1] - 1).start();
            }
        }

        startDownloadMonitor();

        //等待 downloadMonitor 通知下载完成
        try {
            synchronized (waiting) {
                waiting.wait();
            }
        } catch (InterruptedException e) {
            log.error("Download interrupted.", e);
        }

        cleanTempFile();

        long timeElapsed = System.currentTimeMillis() - startTime;
        log.info(localFile + " * File successfully downloaded.");
        log.info(localFile + String.format(" * Time used: %.3f s, Average speed: %d KB/s",
                timeElapsed / 1000.0, downloadedBytes.get() / timeElapsed));
    }

    //检测目标文件是否支持断点续传，以决定是否开启多线程下载文件的不同部分
    public boolean supportResumeDownload() throws IOException {

        int resCode;
        while (true) {
            try {
                HttpGet httpGet = new HttpGet(url.toURI());
                httpGet.setHeader("Range", "bytes=0-");
                httpGet.setHeader("referer", "https://kemono.party/");
                httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(true).setConnectTimeout(TIME_OUT).setSocketTimeout(TIME_OUT).setConnectionRequestTimeout(TIME_OUT).build());
                CloseableHttpResponse execute = httpClient.execute(httpGet);
                fileSize = (int) execute.getEntity().getContentLength();
                resCode = execute.getStatusLine().getStatusCode();
                execute.close();
                break;
            } catch (Exception e) {
                log.info(localFile + " Retry to connect due to connection problem.");
            }
        }
        if (resCode == 206) {
            log.info(localFile + " * Support resume download");
            return true;
        } else {
            log.info(localFile + " * Doesn't support resume download");
            return false;
        }
    }

    //监测下载速度及下载状态，下载完成时通知主线程
    public void startDownloadMonitor() {
        Thread downloadMonitor = new Thread(() -> {
            int prev = 0;
            int curr = 0;
            int i = 0;
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                curr = downloadedBytes.get();
                if (i % 100 == 0) {
                    log.info(localFile + String.format(" Speed: %d KB/s, Downloaded: %d KB (%" +
                            ".2f%%), Threads: %d", (curr - prev) >> 10, curr >> 10,
                            curr / (float) fileSize * 100, aliveThreads.get()));
                }
                prev = curr;
                i++;
                if (aliveThreads.get() == 0) {
                    synchronized (waiting) {
                        waiting.notifyAll();
                    }
                    break;
                }
            }
        });

        downloadMonitor.setDaemon(true);
        downloadMonitor.start();
    }

    //对临时文件进行合并或重命名
    public void cleanTempFile() throws IOException {
        if (multiThreaded) {
            merge();
            log.info(localFile + " * Temp file merged.");
        } else {
            Files.move(Paths.get(localFile.getAbsolutePath() + ".0.tmp"),
                    Paths.get(localFile.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    //合并多线程下载产生的多个临时文件
    public void merge() {
        try (OutputStream out = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[1024];
            int size;
            for (int i = 0; i < THREAD_NUM; i++) {
                String tmpFile = localFile.getAbsolutePath() + "." + i + ".tmp";
                InputStream in = new FileInputStream(tmpFile);
                while ((size = in.read(buffer)) != -1) {
                    out.write(buffer, 0, size);
                }
                in.close();
                Files.delete(Paths.get(tmpFile));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        RequestConfig clientConfig =
                RequestConfig.custom().setRedirectsEnabled(true).setConnectTimeout(60000).setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();

        PoolingHttpClientConnectionManager syncConnectionManager =
                new PoolingHttpClientConnectionManager();

        syncConnectionManager.setMaxTotal(1000);
        syncConnectionManager.setDefaultMaxPerRoute(50);
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        BasicHeader cookieHeader = new BasicHeader("cookie", "__ddg1_=0WN6S68y0aTt7FFGTn6Q; " +
                "_pk_id.1.5bc1=7dc5e3d6da8a845b.1651927189.; __ddg2_=crAhJBbdcp9KgcAo; _pk_ses.1" +
                ".5bc1=1");
        BasicHeader agentHeader = new BasicHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; " + "Win64; x64) " + "AppleWebKit/537.36 (KHTML, " + "like Gecko) Chrome/100.0.4896.127 " + "Safari/537.36");
        CloseableHttpClient httpClient =
                httpClientBuilder.setDefaultHeaders(Lists.newArrayList(cookieHeader, agentHeader)).setDefaultRequestConfig(clientConfig).setRedirectStrategy(new DefaultRedirectStrategy()).setConnectionManager(syncConnectionManager).build();
        String url = "https://kemono" +
                ".party/data/ca/81" +
                "/ca8154c8361b213c8fee0496fcc1354df1dc83535b7cedf9ac498c4ddbcaaf28" +
                ".jpg?f=2f6fc619-1203-4cca-9a04-697f9a0d5672.jpg";
        List<NameValuePair> pairList = new ArrayList<>();
        pairList.add(new BasicNameValuePair("download", url));
        pairList.add(new BasicNameValuePair("cookie", "1"));
        pairList.add(new BasicNameValuePair("send_image", "0"));
        pairList.add(new BasicNameValuePair("res", "1092x1080"));
        pairList.add(new BasicNameValuePair("res", "1092x1080"));
        pairList.add(new BasicNameValuePair("url", "https://kemono.party/fanbox/user/9639655/post" +
                "/4571679"));
        httpClient.execute(new HttpGet("https://kemono.party/onomek?" + EntityUtils.toString(new UrlEncodedFormEntity(pairList))));
        new MultiThreadHttpDownloader(httpClient, url, "D:/ls-lR.txt", 5, 5000).get();
    }

    //一个下载线程负责下载文件的某一部分，如果失败则自动重试，直到下载完成
    class DownloadThread extends Thread {
        private int id;
        private int start;
        private int end;
        private OutputStream out;

        public DownloadThread(int id, int start, int end) {
            this.id = id;
            this.start = start;
            this.end = end;
            aliveThreads.incrementAndGet();
        }

        //保证文件的该部分数据下载完成
        @Override
        public void run() {
            boolean success = false;
            while (true) {
                success = download();
                if (success) {
                    log.info(localFile + " * Downloaded part " + (id + 1));
                    break;
                } else {
                    log.info(localFile + " Retry to download part " + (id + 1));
                }
            }
            aliveThreads.decrementAndGet();
        }

        //下载文件指定范围的部分
        public boolean download() {
            CloseableHttpResponse execute;
            try {
                HttpGet httpGet = new HttpGet(url.toURI());
                httpGet.setHeader("referer", "https://kemono.party/");
                httpGet.setHeader("Range", String.format("bytes=%d-%d", start, end));
                httpGet.setConfig(RequestConfig.custom().setConnectTimeout(TIME_OUT).setSocketTimeout(TIME_OUT).setConnectionRequestTimeout(TIME_OUT).build());
                /*HttpURLConnection con = (HttpURLConnection) url.openConnection(proxy);
                con.setRequestProperty("Range", String.format("bytes=%d-%d", start, end));
                con.setConnectTimeout(TIME_OUT);
                con.setReadTimeout(TIME_OUT);
                con.connect();*/
                execute = httpClient.execute(httpGet);
                HeaderElement[] elements = execute.getFirstHeader("Content-Length").getElements();
                if (elements.length == 0) {
                    return false;
                }
                if (execute.getStatusLine().getStatusCode() == 403) {
                    return false;
                }
                int partSize = Integer.parseInt(elements[0].getName());
                // int partSize = con.getHeaderFieldInt("Content-Length", -1);
                if (partSize != end - start + 1) {
                    return false;
                }
                if (out == null) {
                    out = new FileOutputStream(localFile.getAbsolutePath() + "." + id + ".tmp");
                }
                try (InputStream in = execute.getEntity().getContent()) {
                    byte[] buffer = new byte[1024];
                    int size;
                    while (start <= end && (size = in.read(buffer)) > 0) {
                        start += size;
                        downloadedBytes.addAndGet(size);
                        out.write(buffer, 0, size);
                        out.flush();
                    }
                    execute.close();
                    if (start <= end) {
                        return false;
                    } else {
                        out.close();
                    }
                }
            } catch (SocketTimeoutException e) {
                log.info(localFile + " Part " + (id + 1) + " Reading timeout.");
                return false;
            } catch (IOException e) {
                log.info(localFile + " Part " + (id + 1) + " encountered error.");
                return false;
            } catch (URISyntaxException e) {
                log.info(localFile + " Part " + (id + 1) + " to uri error.");
                return false;
            }

            return true;
        }
    }

}

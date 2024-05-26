package com.dazo66.util;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HeaderElement;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.thymeleaf.util.MapUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicInteger failThreads = new AtomicInteger(0);
    private final int MIN_SIZE = 2 << 20;
    private final CloseableHttpClient httpClient;
    private boolean multiThreaded = true;
    private int fileSize = 0;
    private int THREAD_NUM = 8;
    private int TIME_OUT = 5000;

    private Map<String, String> headers;

    private Map<String, String> cookie;


    private AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();

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

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setCookie(Map<String, String> cookie) {
        this.cookie = cookie;
    }

    //开始下载文件
    public void get() throws Throwable {
        long startTime = System.currentTimeMillis();

        boolean resumable = supportResumeDownload();
        if (!resumable || THREAD_NUM == 1 || fileSize < MIN_SIZE) {
            multiThreaded = false;
        }
        if (!multiThreaded) {
            new DownloadThread(0, 0, fileSize - 1).start();
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

    private CloseableHttpResponse setHeaderAndCookieThenExecute(CloseableHttpClient httpClient, HttpRequestBase request) throws IOException {
        HttpClientContext cookieContext = HttpClientContext.create();
        cookieContext.setCookieStore(new BasicCookieStore());
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
        return httpClient.execute(request, cookieContext);
    }

    //检测目标文件是否支持断点续传，以决定是否开启多线程下载文件的不同部分
    public boolean supportResumeDownload() throws IOException {
        Integer resCode = null;
        Exception e1 = null;
        for (int i = 0; i < 10; i++) {
            try {
                HttpGet httpGet = new HttpGet(url.toURI());
                httpGet.setHeader("Range", "bytes=0-");
                httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(true).setConnectTimeout(TIME_OUT).setSocketTimeout(TIME_OUT).setConnectionRequestTimeout(TIME_OUT).build());
                CloseableHttpResponse execute = setHeaderAndCookieThenExecute(httpClient, httpGet);
                fileSize = (int) execute.getEntity().getContentLength();
                resCode = execute.getStatusLine().getStatusCode();
                execute.close();
                break;
            } catch (Exception e) {
                e1 = e;
                log.info(localFile + " Retry to connect due to connection problem.");
            }
        }
        if (resCode == null) {
            throw new RuntimeException(e1);
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
    public void cleanTempFile() throws Throwable {
        if (multiThreaded) {
            if (failThreads.get() == 0) {
                merge();
            } else {
                cleanTempFileMultiThread();
                if (throwableAtomicReference.get() != null) {
                    throw throwableAtomicReference.get();
                }
            }
            log.info(localFile + " * Temp file merged.");
        } else {
            if (failThreads.get() == 0) {
                Files.move(Paths.get(localFile.getAbsolutePath() + ".0.tmp"),
                        Paths.get(localFile.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Path path = Paths.get(localFile.getAbsolutePath() + ".0.tmp");
                if (Files.exists(path)) {
                    Files.delete(path);
                }
                if (throwableAtomicReference.get() != null) {
                    throw throwableAtomicReference.get();
                }
            }
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

    @SneakyThrows
    public void cleanTempFileMultiThread() {
            for (int i = 0; i < THREAD_NUM; i++) {
                String tmpFile = localFile.getAbsolutePath() + "." + i + ".tmp";

                Path path = Paths.get(tmpFile);
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            }
    }

    public static void main(String[] args) throws Throwable {
        RequestConfig clientConfig =
                RequestConfig.custom().setRedirectsEnabled(true).setConnectTimeout(60000).setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();

        PoolingHttpClientConnectionManager syncConnectionManager =
                new PoolingHttpClientConnectionManager();

        syncConnectionManager.setMaxTotal(1000);
        syncConnectionManager.setDefaultMaxPerRoute(50);
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        BasicHeader agentHeader = new BasicHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; " + "Win64; x64) " + "AppleWebKit/537.36 (KHTML, " + "like Gecko) Chrome/100.0.4896.127 " + "Safari/537.36");
        CloseableHttpClient httpClient =
                httpClientBuilder.setDefaultHeaders(Lists.newArrayList(agentHeader)).setDefaultRequestConfig(clientConfig).setRedirectStrategy(new DefaultRedirectStrategy()).setConnectionManager(syncConnectionManager).build();

        new MultiThreadHttpDownloader(httpClient, "http://127.0.0.1:8080", "D:/ls-lR.jpg", 5, 5000).get();
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
            failThreads.incrementAndGet();
        }

        //保证文件的该部分数据下载完成
        @Override
        public void run() {
            boolean success;
            for (int i = 0; i < 10; i++) {
                success = download();
                if (success) {
                    log.info(localFile + " * Downloaded part " + (id + 1));
                    failThreads.decrementAndGet();
                    break;
                } else {
                    log.info(localFile + " Retry to download part " + (id + 1));
                }
            }
            aliveThreads.decrementAndGet();
        }

        //下载文件指定范围的部分
        public boolean download() {
            CloseableHttpResponse execute = null;
            try {
                HttpGet httpGet = new HttpGet(url.toURI());
                httpGet.setHeader("Range", String.format("bytes=%d-%d", start, end));
                httpGet.setConfig(RequestConfig.custom().setConnectTimeout(TIME_OUT).setSocketTimeout(TIME_OUT).setConnectionRequestTimeout(TIME_OUT).build());
                /*HttpURLConnection con = (HttpURLConnection) url.openConnection(proxy);
                con.setRequestProperty("Range", String.format("bytes=%d-%d", start, end));
                con.setConnectTimeout(TIME_OUT);
                con.setReadTimeout(TIME_OUT);
                con.connect();*/
                execute = setHeaderAndCookieThenExecute(httpClient, httpGet);
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
                    File file = new File(localFile.getAbsolutePath() + "." + id + ".tmp");
                    if (file.exists()) {
                        file.delete();
                    } else {
                        file.getParentFile().mkdir();
                        file.createNewFile();
                    }
                    out = Files.newOutputStream(file.toPath());
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
                throwableAtomicReference.set(e);
                return false;
            } catch (IOException e) {
                log.info(localFile + " Part " + (id + 1) + " encountered error.");
                throwableAtomicReference.set(e);
                return false;
            } catch (URISyntaxException e) {
                log.info(localFile + " Part " + (id + 1) + " to uri error.");
                throwableAtomicReference.set(e);
                return false;
            } finally {
                if (execute != null) {
                    try {
                        execute.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            return true;
        }
    }

}

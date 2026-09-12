package com.dazo66.util;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;

import java.io.*;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
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
    private final OkHttpClient httpClient;
    private boolean multiThreaded = true;
    private int fileSize = 0;
    private int THREAD_NUM = 8;
    private int TIME_OUT = 5000;

    private Map<String, String> headers;

    private Map<String, String> cookie;


    private AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();

    public MultiThreadHttpDownloader(OkHttpClient httpClient, String url,
                                     String localPath) throws MalformedURLException {
        this.httpClient = httpClient;
        this.url = new URL(url);
        this.localFile = new File(localPath);
    }

    public MultiThreadHttpDownloader(OkHttpClient httpClient, String url, String localPath
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

    private Response setHeaderAndCookieThenExecute(OkHttpClient httpClient, Request.Builder request) throws IOException {
        HttpClientContext cookieContext = HttpClientContext.create();
        cookieContext.setCookieStore(new BasicCookieStore());
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.header(entry.getKey(), entry.getValue());
            }
        }
        if (cookie != null && !cookie.isEmpty()) {
            StringBuilder cookieBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : cookie.entrySet()) {
                cookieBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
            }
            request.addHeader("Cookie", cookieBuilder.toString());
        }
        return httpClient.newCall(request.build()).execute();
    }

    //检测目标文件是否支持断点续传，以决定是否开启多线程下载文件的不同部分
    public boolean supportResumeDownload() throws IOException {
        Boolean resCode = null;
        Exception e1 = null;
        for (int i = 0; i < 10; i++) {
            try {
                com.squareup.okhttp.Request.Builder builder = new Request.Builder().url(url);
                builder.header("Range", "bytes=0-");
                Response execute = setHeaderAndCookieThenExecute(httpClient, builder);
                fileSize = (int) execute.body().contentLength();
                resCode = execute.isSuccessful();
                break;
            } catch (Exception e) {
                e1 = e;
                log.info(localFile + " Retry to connect due to connection problem.");
            }
        }
        if (resCode == null) {
            throw new RuntimeException(e1);
        }
        if (resCode) {
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
        new MultiThreadHttpDownloader(new OkHttpClient(), "https://repo.huaweicloud.com/java/jdk/11.0.2+9/jdk-11.0.2_windows-x64_bin.exe", "jdk-11.0.2_windows-x64_bin.exe", 5, 5000).get();
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
            Response execute = null;
            try {
                com.squareup.okhttp.Request.Builder builder = new Request.Builder().url(url);
                builder.header("Range", String.format("bytes=%d-%d", start, end));
                /*HttpURLConnection con = (HttpURLConnection) url.openConnection(proxy);
                con.setRequestProperty("Range", String.format("bytes=%d-%d", start, end));
                con.setConnectTimeout(TIME_OUT);
                con.setReadTimeout(TIME_OUT);
                con.connect();*/
                execute = setHeaderAndCookieThenExecute(httpClient, builder);
                String length = execute.header("Content-Length");
                if (StringUtils.isEmpty(length)) {
                    return false;
                }
                if (!execute.isSuccessful()) {
                    return false;
                }
                int partSize = Integer.parseInt(length);
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
                try (InputStream in = execute.body().byteStream()) {
                    byte[] buffer = new byte[1024];
                    int size;
                    while (start <= end && (size = in.read(buffer)) > 0) {
                        start += size;
                        downloadedBytes.addAndGet(size);
                        out.write(buffer, 0, size);
                        out.flush();
                    }
                    in.close();
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
            } finally {
                // ignore
            }

            return true;
        }
    }

}

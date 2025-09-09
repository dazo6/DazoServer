package com.dazo66.crawler;


import com.alibaba.fastjson.JSONObject;
import com.dazo66.entity.CrawlerRequest;
import com.dazo66.service.CrawlerRequestService;
import com.dazo66.util.SpringContextUtils;
import com.geccocrawler.gecco.annotation.Downloader;
import com.geccocrawler.gecco.downloader.AbstractDownloader;
import com.geccocrawler.gecco.downloader.DownloadException;
import com.geccocrawler.gecco.downloader.DownloadServerException;
import com.geccocrawler.gecco.downloader.UserAgent;
import com.geccocrawler.gecco.downloader.proxy.Proxys;
import com.geccocrawler.gecco.downloader.proxy.ProxysContext;
import com.geccocrawler.gecco.request.HttpPostRequest;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.response.HttpResponse;
import com.geccocrawler.gecco.spider.SpiderThreadLocal;
import com.squareup.okhttp.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.util.CharArrayBuffer;

import java.io.*;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.dazo66.crawler.kemono.KemonoContents.KEMONO_HOST;
import static java.net.Proxy.Type.HTTP;

@Downloader("kemonoDownloader")
@Slf4j
public class KemonoDownloader extends AbstractDownloader {


    private OkHttpClient httpClient;

    private boolean isSpringHttpClient = false;

    private Map<String, HttpCookie> cookieMap = new ConcurrentHashMap<>();

    public KemonoDownloader() {

        OkHttpClient fanboxHttpClient = SpringContextUtils.getBean("fanboxOkHttpClient");
        if (fanboxHttpClient != null) {
            log.info("gecco use spring http client!");
            httpClient = fanboxHttpClient;
            isSpringHttpClient = true;
        } else {
            httpClient = new OkHttpClient();
        }
    }


    @Override
    public HttpResponse download(HttpRequest request, int timeout) throws DownloadException {
        if (log.isDebugEnabled()) {
            log.debug("downloading... {}", JSONObject.toJSONString(request));
        }
        Request.Builder reqObj = null;
        if (request instanceof HttpPostRequest) {//post
            HttpPostRequest post = (HttpPostRequest) request;
            StringBuilder builder = new StringBuilder();

            post.getFields().forEach((name, value) -> {
                if (builder.length() != 0) {
                    builder.append('&');
                }
                builder.append(name);
                if (value != null) {
                    builder.append('=');
                    builder.append(value);
                }

            });

            RequestBody requestBody = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), builder.toString());
            reqObj = new Request.Builder().url(post.getUrl()).method("POST", requestBody);

        } else {//get
            reqObj = new Request.Builder().url(request.getUrl()).get();
        }
        //header
        boolean isMobile = SpiderThreadLocal.get().getEngine().isMobile();
        reqObj.header("User-Agent", UserAgent.getUserAgent(isMobile));
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            reqObj.header(entry.getKey(), entry.getValue());
        }
        //proxy
        HttpHost proxy = null;
        Proxys proxys = ProxysContext.get();
        boolean isProxy = ProxysContext.isEnableProxy();
        if (proxys != null && isProxy) {
            proxy = proxys.getProxy();
            if (proxy != null) {
                log.debug("proxy:" + proxy.getHostName() + ":" + proxy.getPort());
                httpClient.setProxy(new Proxy(HTTP, new InetSocketAddress(proxy.getHostName(), proxy.getPort())));
            }
        }
        //request and response
        try {
            StringBuilder cookieBuilder = new StringBuilder();
            if (request.getCookies() != null) {
                for (Map.Entry<String, String> entry : request.getCookies().entrySet()) {
                    cookieBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
                }
            }

            for (Map.Entry<String, HttpCookie> entry : cookieMap.entrySet()) {
                if (!entry.getValue().hasExpired()) {
                    cookieBuilder.append(entry.getKey()).append("=").append(entry.getValue().getValue()).append(";");
                }
            }
            reqObj.header("accept", "text/css");
            reqObj.header("Cookie", cookieBuilder.toString());
            Response response = httpClient.newCall(reqObj.build()).execute();
            int status = response.code();
            HttpResponse resp = new HttpResponse();
            resp.setStatus(status);
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                ByteArrayInputStream raw = toByteInputStream(body.byteStream());
                resp.setRaw(raw);
                MediaType contentType = body.contentType();
                resp.setContentType(contentType.toString());
                if (!isImage(contentType.toString())) {
                    String charset = getCharset(request.getCharset(), contentType.toString());
                    resp.setCharset(charset);
                    //String content = EntityUtils.toString(responseEntity, charset);
                    String content = getContent(raw, body.contentLength(), charset);
                    resp.setContent(content);
                }
            } else {

                if (response.code() == 403 && request.getUrl().contains(KEMONO_HOST)) {
                    boolean needRetry = false;
                    if (cookieMap.isEmpty()) {
                        needRetry = true;
                    }
                    for (Map.Entry<String, HttpCookie> cookieEntry : cookieMap.entrySet()) {
                        if (cookieEntry.getValue().hasExpired()) {
                            needRetry = true;
                        }
                    }
                    log.error("403错误，可能是cookie失效，尝试刷新cookie");
                    List<String> headers = response.headers("Set-Cookie");
                    if (headers != null && headers.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (String header : headers) {
                            List<HttpCookie> cookies = HttpCookie.parse(header);
                            log.info(cookies.toString());
                            for (HttpCookie cookie : cookies) {
                                cookieMap.put(cookie.getName(), cookie);
                            }
/*                            for (String cookie : cookies) {
                                if (sb.length() > 0) {
                                    sb.append(";");
                                }
                                sb.append(cookie);
                            }*/
                        }

                    }
                    if (needRetry) return download(request, timeout);

                }

                //404，500等
                if (proxy != null) {
                    proxys.failure(proxy.getHostName(), proxy.getPort());
                }
                log.debug("download error: {} , {}", response.code(), IOUtils.toString(response.body().byteStream()));
                if (response.code() == 400 && request.getUrl().contains("posts")) {
                    if (SpringContextUtils.getApplicationContext() != null) {
                        SpringContextUtils.getBean(CrawlerRequestService.class).updateByUrl(new CrawlerRequest().setUrl(request.getUrl()).setIsDone(true));
                    }
                }
                throw new DownloadServerException(" " + status);
            }
            if (proxy != null) {
                proxys.success(proxy.getHostName(), proxy.getPort());
            }
            return resp;
        } catch (IOException e) {
            //超时等
            if (proxy != null) {
                proxys.failure(proxy.getHostName(), proxy.getPort());
            }
            throw new DownloadException(e);
        }
    }

    @Override
    public void shutdown() {
    }

    public String getContent(InputStream instream, long contentLength, String charset) throws IOException {
        try {
            if (instream == null) {
                return null;
            }
            int i = (int) contentLength;
            if (i < 0) {
                i = 4096;
            }
            Reader reader = new InputStreamReader(instream, charset);
            CharArrayBuffer buffer = new CharArrayBuffer(i);
            char[] tmp = new char[1024];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buffer.append(tmp, 0, l);
            }
            return buffer.toString();
        } finally {
            instream.reset();
        }

    }

    private boolean isImage(String contentType) {
        if (contentType == null) {
            return false;
        }
        if (contentType.toLowerCase().startsWith("image")) {
            return true;
        }
        return false;
    }
}

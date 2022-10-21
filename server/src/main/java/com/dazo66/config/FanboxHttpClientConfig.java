package com.dazo66.config;

import com.dazo66.util.CloseableHttpClientWrapper;
import com.dazo66.util.IpUtils;
import com.dazo66.util.KemonoHelper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.solr.common.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author Dazo66
 */
@Configuration
@Slf4j
public class FanboxHttpClientConfig {

    @Value("${fanbox.proxy.ip}")
    private String proxyIp;
    @Value("${fanbox.proxy.port}")
    private Integer proxyPort;

    @Value("${kemono.username}")
    private String kemonoUsername;

    @Value("${kemono.password}")
    private String kemonoPassword;

    @Autowired
    @Lazy
    private CloseableHttpClient fanboxHttpClient;

    @Bean("fanboxHttpClient")
    public CloseableHttpClient warpperClient() {
        return new CloseableHttpClientWrapper(createHttpclient());
    }

    public CloseableHttpClient createHttpclient() {
        RequestConfig clientConfig =
                RequestConfig.custom().setConnectTimeout(60000).setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();

        PoolingHttpClientConnectionManager syncConnectionManager =
                new PoolingHttpClientConnectionManager();

        syncConnectionManager.setMaxTotal(1000);
        syncConnectionManager.setDefaultMaxPerRoute(50);
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (IpUtils.checkIpPort(proxyIp, proxyPort)) {
            log.info("代理可用，开始使用代理通道");
            httpClientBuilder.setProxy(new HttpHost(proxyIp, proxyPort));
        }
        ArrayList<BasicHeader> headers = new ArrayList<>();
        String cookie = KemonoHelper.loginAndGetCookie(kemonoUsername, kemonoPassword);
        if (StringUtils.isEmpty(cookie)) {
            log.info("更新cookie失败");
        } else {
            BasicHeader cookieHeader = new BasicHeader("cookie", cookie);
            log.info("获得cookie {}", cookie);
            headers.add(cookieHeader);
        }
        BasicHeader agentHeader = new BasicHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; " + "Win64; x64) " + "AppleWebKit/537.36 (KHTML, " + "like Gecko) Chrome/100.0.4896.127 " + "Safari/537.36");
        headers.add(agentHeader);
        return httpClientBuilder
                .setDefaultHeaders(headers)
                .setDefaultRequestConfig(clientConfig)
                .setRedirectStrategy(new DefaultRedirectStrategy())
                .setConnectionManager(syncConnectionManager)
                .build();
    }

    @Scheduled(initialDelay = 24, fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    public void updateCookie() {
        log.info("更新 kemono cookie 开始");

            synchronized (fanboxHttpClient) {
                CloseableHttpClientWrapper wrapper = (CloseableHttpClientWrapper) this.fanboxHttpClient;
                try {
                    wrapper.getClient().close();
                } catch (IOException e) {
                    // ignore
                }
                wrapper.setClient(createHttpclient());
            }

    }

    @Bean
    public Proxy getProxy() {
        Proxy proxy1 = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyIp, proxyPort));
        proxy = proxy1;
        return proxy1;
    }

    public static Proxy proxy = Proxy.NO_PROXY;

}

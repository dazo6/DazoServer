package com.dazo66.config;

import com.dazo66.util.CloseableHttpClientWrapper;
import com.dazo66.util.IpUtils;
import com.dazo66.util.SpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
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


    @Bean("fanboxHttpClient")
    public CloseableHttpClient warpperClient() {
        return new CloseableHttpClientWrapper(createHttpclient());
    }

    public CloseableHttpClient createHttpclient() {
        RequestConfig clientConfig = RequestConfig.custom().setConnectTimeout(60000).setConnectionRequestTimeout(60000).setSocketTimeout(60000).build();

        PoolingHttpClientConnectionManager syncConnectionManager = new PoolingHttpClientConnectionManager();
        syncConnectionManager.setMaxTotal(1000);
        syncConnectionManager.setDefaultMaxPerRoute(50);
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();

        if (IpUtils.checkIpPort(proxyIp, proxyPort)) {
            log.info("代理可用，开始使用代理通道");
            httpClientBuilder.setProxy(new HttpHost(proxyIp, proxyPort));
        }

        CloseableHttpClient client = httpClientBuilder
                // .setDefaultHeaders(headers)
                .setDefaultRequestConfig(clientConfig)
                .setRedirectStrategy(new DefaultRedirectStrategy())
                .setConnectionManager(syncConnectionManager)
                .build();
        return client;
    }

    @Scheduled(initialDelay = 24, fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    public void updateCookie() {
        log.info("更新 kemono cookie 开始");
        CloseableHttpClientWrapper httpClient = SpringContextUtils.getBean("fanboxHttpClient");
        synchronized (httpClient) {
            try {
                httpClient.getClient().close();
            } catch (IOException e) {
                // ignore
            }
            httpClient.setClient(createHttpclient());
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

package com.dazo66.config;

import com.dazo66.util.IpUtils;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    @Value("${fanbox.cookie}")
    private String cookie;

    @Bean("fanboxHttpClient")
    public CloseableHttpClient init() {
        RequestConfig clientConfig =
                RequestConfig.custom().setConnectTimeout(60000).setConnectionRequestTimeout(6000).setSocketTimeout(6000).build();

        PoolingHttpClientConnectionManager syncConnectionManager =
                new PoolingHttpClientConnectionManager();

        syncConnectionManager.setMaxTotal(1000);
        syncConnectionManager.setDefaultMaxPerRoute(50);
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (IpUtils.checkIpPort(proxyIp, proxyPort)) {
            log.info("代理可用，开始使用代理通道");
            httpClientBuilder.setProxy(new HttpHost(proxyIp, proxyPort));
        }
        BasicHeader cookieHeader = new BasicHeader("cookie", cookie);
        BasicHeader agentHeader = new BasicHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; " +
                "Win64; x64) " + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 " +
                "Safari/537.36");
        return httpClientBuilder.setDefaultHeaders(Lists.newArrayList(cookieHeader, agentHeader)).setDefaultRequestConfig(clientConfig).setRedirectStrategy(new DefaultRedirectStrategy()).setConnectionManager(syncConnectionManager).build();
    }

}

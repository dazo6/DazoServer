package com.dazo66.config;

import com.squareup.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean("fanboxOkHttpClient")
    public OkHttpClient okHttpClient(@Autowired Proxy proxy) {
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(1L, TimeUnit.MINUTES);
        okHttpClient.setReadTimeout(5L, TimeUnit.MINUTES);
        okHttpClient.setProxy(proxy);
        return okHttpClient;
    }


    @Bean
    public Proxy getProxy() {
        Proxy proxy1 = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyIp, proxyPort));
        proxy = proxy1;
        return proxy1;
    }

    public static Proxy proxy = Proxy.NO_PROXY;

}

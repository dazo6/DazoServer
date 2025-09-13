package com.dazo66.config;

import com.squareup.okhttp.OkHttpClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.CertificateException;
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

    @Getter
    @Value("${ja3.proxy.enable:false}")
    private boolean ja3ProxyEnable;
    @Getter
    @Value("${ja3.proxy.address:0}")
    private String ja3Address;
    @Getter
    @Value("${ja3.proxy.user.agent:}")
    private String ja3ProxyUserAgent;
    @Getter
    @Value("${ja3.proxy.ja3:0}")
    private String ja3ProxyJa3;


    @Bean("fanboxOkHttpClient")
    public OkHttpClient okHttpClient(@Autowired Proxy proxy) {
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(1L, TimeUnit.MINUTES);
        okHttpClient.setReadTimeout(5L, TimeUnit.MINUTES);
        okHttpClient.setProxy(proxy);
        return okHttpClient;
    }

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient builder = new OkHttpClient();
            builder.setSslSocketFactory(sslSocketFactory);
            builder.setHostnameVerifier((hostname, session) -> true);
            builder.setReadTimeout(30, TimeUnit.SECONDS);
            builder.setConnectTimeout(10, TimeUnit.SECONDS);
            builder.setWriteTimeout(10, TimeUnit.SECONDS);
            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Bean
    public Proxy getProxy() {
        Proxy proxy1 = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
        proxy = proxy1;
        return proxy1;
    }

    public static Proxy proxy = Proxy.NO_PROXY;

}

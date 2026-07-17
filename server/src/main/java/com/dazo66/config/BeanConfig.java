package com.dazo66.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Dazo66
 */
@Configuration
public class BeanConfig {

    @Bean("ioLockObject")
    public Object getExecutor() {
        return new Object();
    }

    @Bean("downloadThreadPool")
    public ThreadPoolExecutor getDownloadThreadPool() {
        return new ThreadPoolExecutor(20, 20, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("download-%02d").build());
    }

}

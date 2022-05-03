package com.dazo66.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dazo66
 */
@Configuration
public class BeanConfig {

    @Bean("ioLockObject")
    public Object getExecutor() {
        return new Object();
    }


}

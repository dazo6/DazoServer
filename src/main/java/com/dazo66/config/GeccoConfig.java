package com.dazo66.config;


import com.geccocrawler.gecco.spring.SpringPipelineFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dazo66
 */
@Configuration
public class GeccoConfig {

    @Bean
    public SpringPipelineFactory getSpringPipelineFactory() {
        return new SpringPipelineFactory();
    }

}

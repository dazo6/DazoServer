package com.dazo66.config;


import com.geccocrawler.gecco.spider.SpiderBeanFactory;
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

    @Bean
    public SpiderBeanFactory getSpiderBeanFactory() {
        return new SpiderBeanFactory("com.dazo66", getSpringPipelineFactory());
    }

}

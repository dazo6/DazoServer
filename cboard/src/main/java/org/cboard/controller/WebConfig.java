package org.cboard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@ComponentScan(basePackages = {"org.cboard.controller"})
@ImportResource({"classpath:spring.xml", "classpath:spring-mvc.xml", "classpath:spring-security.xml"})
@PropertySource(value = {"classpath:config.properties"})
public class WebConfig extends WebMvcConfigurerAdapter {

    @Value("${resource.cache-control.max-age:1200}")
    private Integer maxAge;

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigIn() {
        return new PropertySourcesPlaceholderConfigurer();
    }



/*    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        super.addResourceHandlers(registry);

        registry.addResourceHandler("/**").addResourceLocations("/").addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(maxAge, TimeUnit.SECONDS));

    }*/
}

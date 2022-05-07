package com.dazo66;


import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Dazo66
 */
@SpringBootApplication(scanBasePackages = {"com.dazo66", "org.cboard"})
@MapperScans({@MapperScan("com.dazo66.mapper"), @MapperScan("org.cboard.dao")})
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableTransactionManagement
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

}

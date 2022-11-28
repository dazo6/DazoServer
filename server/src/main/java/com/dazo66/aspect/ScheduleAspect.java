package com.dazo66.aspect;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class ScheduleAspect {

    @Value("${server.env}")
    private String env;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object doBasicProfiling(ProceedingJoinPoint pjp) throws Throwable {

        if ("test".equalsIgnoreCase(env)) {
            log.info("测试环境 跳过定时方法：{}", pjp.getSignature().getName());
            return null;
        } else {
            return pjp.proceed();
        }

    }

}

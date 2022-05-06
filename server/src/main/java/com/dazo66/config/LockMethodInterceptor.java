package com.dazo66.config;

import com.dazo66.util.SpringContextUtils;
import com.dazo66.util.LocalLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class LockMethodInterceptor {

    @Around("@annotation(com.dazo66.util.LocalLock) || @within(com.dazo66.util.LocalLock)")
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        LocalLock localLock = method.getAnnotation(LocalLock.class);
        if (localLock == null) {
            localLock = pjp.getTarget().getClass().getAnnotation(LocalLock.class);
        }
        if (localLock == null) {
            return pjp.proceed();
        }
        String key = localLock.lockBeanName();
        Object bean = SpringContextUtils.getBean(key);
        if (bean == null) {
            return pjp.proceed();
        }
        synchronized (bean) {
            return pjp.proceed();
        }
    }

}
package com.dazo66.aspect;

import lombok.extern.slf4j.Slf4j;
import com.dazo66.service.ScheduledTaskRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect @Slf4j @Component @Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
public class ScheduleAspect {
    private static final ThreadLocal<ScheduledTaskRegistry.Entry> CURRENT_TASK = new ThreadLocal<>();
    public static ScheduledTaskRegistry.Entry getCurrentTask() { return CURRENT_TASK.get(); }
    private final ScheduledTaskRegistry registry;
    private final String env;
    public ScheduleAspect(ScheduledTaskRegistry registry, @Value("${server.env}") String env) {
        this.registry=registry; this.env=env;
    }
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) || @annotation(org.springframework.scheduling.annotation.Schedules)")
    public Object doBasicProfiling(ProceedingJoinPoint pjp) throws Throwable {
        if ("test".equalsIgnoreCase(env)) return null;
        if (CURRENT_TASK.get() != null) return pjp.proceed();
        ScheduledTaskRegistry.Entry task = registry.find(pjp.getTarget(), ((MethodSignature)pjp.getSignature()).getMethod());
        boolean manual=registry.isManual(task);
        if (!manual && !task.running.compareAndSet(false, true)) {
            log.info("Skipping overlapping scheduled invocation: {}", pjp.getSignature().getName());
            return null;
        }
        CURRENT_TASK.set(task);
        task.started(manual);
        boolean success=false;
        try { Object result=pjp.proceed(); success=true; return result; }
        finally {
            CURRENT_TASK.remove();
            task.finished(success);
            // Manual caller releases after proxy invocation completes, including other interceptors.
            if (!manual) task.running.set(false);
        }
    }
}

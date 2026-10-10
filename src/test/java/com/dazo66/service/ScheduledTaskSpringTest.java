package com.dazo66.service;

import com.dazo66.aspect.ScheduleAspect;
import org.junit.jupiter.api.Test;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ScheduledTaskSpringTest {
    @Test void realSpringScheduledRunnableAndConfigurationBeanUseSameGuard() throws Exception {
        try(AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext(Bootstrap.class)) {
            ScheduledTaskRegistry registry=context.getBean(ScheduledTaskRegistry.class);
            ScheduledConfig bean=context.getBean(ScheduledConfig.class);
            String id=registry.list().stream().filter(t->t.getName().equals("tick")).findFirst().get().getId();
            ScheduledAnnotationBeanPostProcessor processor=context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            Runnable periodic=processor.getScheduledTasks().stream().map(t->t.getTask().getRunnable())
                .filter(r->r instanceof ScheduledMethodRunnable && ((ScheduledMethodRunnable)r).getMethod().getName().equals("tick")).findFirst().get();
            registry.runManually(id);
            assertThat(bean.entered().await(5,TimeUnit.SECONDS)).isTrue();
            try {
                periodic.run();
                assertThat(bean.count()).isEqualTo(1);
                assertThat(registry.list()).hasSize(1);
            } finally {bean.release();}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(registry.list().get(0).isRunning() && System.nanoTime()<deadline) Thread.sleep(10);
            assertThat(registry.list().get(0).isRunning()).isFalse();
            periodic.run();
            assertThat(bean.count()).isEqualTo(2);
            assertThat(registry.list().get(0).getLastSource()).isEqualTo("scheduled");
        }
    }
    @Configuration @EnableScheduling @EnableAspectJAutoProxy(proxyTargetClass=true) @Import(ScheduledConfig.class)
    static class Bootstrap {
        @Bean ScheduledTaskRegistry registry(ApplicationContext c,ObjectProvider<TaskScheduler> s){return new ScheduledTaskRegistry(c,s,"prod");}
        @Bean ScheduleAspect aspect(ScheduledTaskRegistry r){return new ScheduleAspect(r,"prod");}
        @Bean ThreadPoolTaskScheduler taskScheduler(){ThreadPoolTaskScheduler s=new ThreadPoolTaskScheduler();s.setPoolSize(2);return s;}
    }
    @Configuration
    public static class ScheduledConfig {
        private final CountDownLatch entered=new CountDownLatch(1),done=new CountDownLatch(1);
        private final AtomicInteger count=new AtomicInteger();
        @Scheduled(initialDelay=3600000,fixedDelay=3600000)
        public void tick() {
            count.incrementAndGet();entered.countDown();
            try {if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
        }
        public CountDownLatch entered(){return entered;}
        public int count(){return count.get();}
        public void release(){done.countDown();}
    }
}

package com.dazo66.service;

import com.dazo66.aspect.ScheduleAspect;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduledTaskRegistryTest {
    private StaticApplicationContext context;
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledTaskRegistry registry;
    private Work target, proxy;
    @AfterEach void close() { if(scheduler!=null) scheduler.shutdown(); if(context!=null) context.close(); }
    void setup(TaskScheduler custom, String env) {
        context=new StaticApplicationContext();
        if(custom==null) {
            scheduler=new ThreadPoolTaskScheduler(); scheduler.setPoolSize(2);
            scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); scheduler.initialize(); custom=scheduler;
        }
        context.getBeanFactory().registerSingleton("taskScheduler", custom);
        registry=new ScheduledTaskRegistry(context,context.getBeanProvider(TaskScheduler.class),env);
        target=new Work();
        AspectJProxyFactory factory=new AspectJProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAspect(new ScheduleAspect(registry,env)); proxy=factory.getProxy();
        context.getBeanFactory().registerSingleton("workBean",proxy);
        context.getBeanFactory().registerSingleton("otherBean",new Work());
        registry.afterSingletonsInstantiated();
    }
    String id(String name) { return registry.list().stream().filter(t->t.getBeanName().equals("workBean") && t.getName().equals(name)).findFirst().get().getId(); }
    ScheduledTaskRegistry.TaskView view(String name) {return registry.list().stream().filter(t->t.getId().equals(id(name))).findFirst().get();}
    void waitIdle(String name) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(view(name).isRunning() && System.nanoTime()<end) Thread.sleep(10);
        assertThat(view(name).isRunning()).isFalse();
    }
    @Test void inventoriesInheritedRepeatableAndSameNamedMethodsPerBean() {
        setup(null,"prod");
        assertThat(registry.list()).hasSize(6);
        assertThat(view("work").getRules()).hasSize(2);
        assertThat(registry.list().stream().map(ScheduledTaskRegistry.TaskView::getId).distinct().count()).isEqualTo(6);
        assertThat(view("inherited").getName()).isEqualTo("inherited");
    }
    @Test void manualIsAsynchronousAndScheduledTriggerCannotOverlap() throws Exception {
        setup(null,"prod"); target.block=true;
        registry.runManually(id("work"));
        assertThat(target.entered.await(5,TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(view("work").isRunning()).isTrue();
            assertThat(view("work").getLastSource()).isEqualTo("manual");
            assertThatThrownBy(()->registry.runManually(id("work"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            proxy.work();
            assertThat(target.calls.get()).isEqualTo(1);
        } finally {target.release.countDown();}
        waitIdle("work");
        assertThat(view("work").getLastResult()).isEqualTo("SUCCESS");
        assertThat(view("work").getFinishedAt()).isNotNull();
        registry.runManually(id("work")); waitIdle("work");
        assertThat(target.calls.get()).isEqualTo(2);
    }
    @Test void scheduledExecutionBlocksManualRequests() throws Exception {
        setup(null,"prod"); target.block=true;
        Thread thread=new Thread(proxy::work); thread.start();
        assertThat(target.entered.await(5,TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(view("work").getLastSource()).isEqualTo("scheduled");
            assertThatThrownBy(()->registry.runManually(id("work"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        } finally {target.release.countDown();thread.join(5000);}
        waitIdle("work");
    }
    @Test void reservesBeforeAsyncExecutionAndRollsBackRejection() {
        TaskScheduler mock=mock(TaskScheduler.class);
        when(mock.schedule(any(Runnable.class),any(Date.class))).thenReturn(mock(ScheduledFuture.class));
        setup(mock,"prod");
        registry.runManually(id("work"));
        assertThat(view("work").isRunning()).isTrue();
        proxy.work(); assertThat(target.calls.get()).isZero();
        assertThatThrownBy(()->registry.runManually(id("work"))).hasMessageContaining("409");
        when(mock.schedule(any(Runnable.class),any(Date.class))).thenThrow(new RejectedExecutionException());
        assertThatThrownBy(()->registry.runManually(id("fail"))).hasMessageContaining("503");
        assertThat(view("fail").isRunning()).isFalse();
    }
    @Test void failureReleasesStateAndClearsManualThreadContext() throws Exception {
        setup(null,"prod");
        registry.runManually(id("fail")); waitIdle("fail");
        assertThat(view("fail").getLastResult()).isEqualTo("FAILED");
        registry.runManually(id("inherited")); waitIdle("inherited");
        assertThat(view("inherited").getLastResult()).isEqualTo("SUCCESS");
        assertThatThrownBy(proxy::fail).isInstanceOf(IllegalStateException.class);
        assertThat(view("fail").isRunning()).isFalse();
        assertThat(view("fail").getLastSource()).isEqualTo("scheduled");
    }
    @Test void preservesTestEnvironmentSkipAndRejectsUnknownIds() {
        setup(null,"test"); proxy.work(); assertThat(target.calls.get()).isZero();
        assertThat(view("work").isManualEnabled()).isFalse();
        assertThatThrownBy(()->registry.runManually(id("work"))).hasMessageContaining("400");
        assertThatThrownBy(()->registry.runManually("unknown")).hasMessageContaining("404");
    }
    public static class Base {
        @Scheduled(fixedDelay=1000000) public void inherited() {}
    }
    public static class Work extends Base {
        final AtomicInteger calls=new AtomicInteger();
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        volatile boolean block;
        @Scheduled(fixedDelay=1000000) @Scheduled(cron="0 0 0 * * *")
        public void work() {
            calls.incrementAndGet(); entered.countDown();
            if(block) try {if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");} catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
        }
        @Scheduled(fixedDelay=1000000) public void fail() {throw new IllegalStateException("expected failure");}
    }
}

package com.dazo66.service;
import com.dazo66.aspect.ScheduleAspect;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;
class ScheduledTaskNestedTest {
    StaticApplicationContext context;
    ThreadPoolTaskScheduler scheduler;
    ScheduledTaskRegistry registry;
    Job a,b,aTarget,bTarget;
    @BeforeEach void setup(){
        context=new StaticApplicationContext();scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.initialize();
        context.getBeanFactory().registerSingleton("taskScheduler",scheduler);
        registry=new ScheduledTaskRegistry(context,context.getBeanProvider(TaskScheduler.class),"prod");
        aTarget=new Job();bTarget=new Job();a=proxy(aTarget);b=proxy(bTarget);
        context.getBeanFactory().registerSingleton("a",a);context.getBeanFactory().registerSingleton("b",b);registry.afterSingletonsInstantiated();
    }
    Job proxy(Job target){AspectJProxyFactory f=new AspectJProxyFactory(target);f.setProxyTargetClass(true);f.addAspect(new ScheduleAspect(registry,"prod"));return f.getProxy();}
    ScheduledTaskRegistry.TaskView view(String name){return registry.list().stream().filter(t->t.getBeanName().equals(name)).findFirst().get();}
    @AfterEach void close(){scheduler.shutdown();context.close();assertThat(ScheduleAspect.getCurrentTask()).isNull();}
    @Test void nestedInvocationKeepsOuterContextAndPreservesInnerHistory(){
        b.run();Long previous=view("b").getStartedAt(),finished=view("b").getFinishedAt();
        bTarget.action=()->{assertThat(ScheduleAspect.getCurrentTask()).isSameAs(registry.find(aTarget,method()));assertThat(view("a").isRunning()).isTrue();assertThat(view("b").isRunning()).isFalse();};
        aTarget.action=b::run;a.run();
        assertThat(view("a").getLastResult()).isEqualTo("SUCCESS");
        assertThat(view("b").getStartedAt()).isEqualTo(previous);assertThat(view("b").getFinishedAt()).isEqualTo(finished);
    }
    @Test void nestedFailureRecordsOnlyOuterFailureAndClearsContext(){
        bTarget.action=()->{throw new IllegalStateException("nested failure");};aTarget.action=b::run;
        assertThatThrownBy(a::run).isInstanceOf(IllegalStateException.class);
        assertThat(view("a").getLastResult()).isEqualTo("FAILED");assertThat(view("b").getStartedAt()).isNull();
        assertThat(ScheduleAspect.getCurrentTask()).isNull();bTarget.action=()->{};b.run();assertThat(view("b").getLastResult()).isEqualTo("SUCCESS");
    }
    @Test void manualInvocationAlsoKeepsOuterContextOnReusableWorker() throws Exception {
        bTarget.action=()->{assertThat(ScheduleAspect.getCurrentTask()).isSameAs(registry.find(aTarget,method()));};aTarget.action=b::run;
        registry.runManually(view("a").getId());waitIdle("a");
        assertThat(view("a").getLastSource()).isEqualTo("manual");assertThat(view("a").getLastResult()).isEqualTo("SUCCESS");assertThat(view("b").getStartedAt()).isNull();
        bTarget.action=()->{assertThat(ScheduleAspect.getCurrentTask()).isSameAs(registry.find(bTarget,method()));};
        registry.runManually(view("b").getId());waitIdle("b");assertThat(view("b").getLastResult()).isEqualTo("SUCCESS");
    }
    void waitIdle(String name)throws Exception{long end=System.currentTimeMillis()+5000;while(view(name).isRunning() && System.currentTimeMillis()<end)Thread.sleep(10);assertThat(view(name).isRunning()).isFalse();}
    java.lang.reflect.Method method(){try{return Job.class.getMethod("run");}catch(Exception e){throw new IllegalStateException(e);}}
    public static class Job {Runnable action=()->{};@Scheduled(fixedDelay=1000000) public void run(){action.run();}}
}

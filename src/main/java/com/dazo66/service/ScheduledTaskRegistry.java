package com.dazo66.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ScheduledTaskRegistry implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledTaskRegistry.class);
    private final ApplicationContext context;
    private final ObjectProvider<TaskScheduler> scheduler;
    private final boolean testEnvironment;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final IdentityHashMap<Object, Map<Method, Entry>> targets = new IdentityHashMap<>();
    private final ThreadLocal<Entry> manual = new ThreadLocal<>();

    public ScheduledTaskRegistry(ApplicationContext context, ObjectProvider<TaskScheduler> scheduler,
                                 @Value("${server.env}") String environment) {
        this.context = context; this.scheduler = scheduler;
        this.testEnvironment = "test".equalsIgnoreCase(environment);
    }

    @Override public void afterSingletonsInstantiated() {
        context.getBeansOfType(Object.class).forEach((name, bean) -> {
            Class<?> type = AopUtils.getTargetClass(bean);
            Map<Method, Set<Scheduled>> methods = MethodIntrospector.selectMethods(type,
                (MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
                    Set<Scheduled> annotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class);
                    return annotations.isEmpty() ? null : annotations;
                });
            Object target = AopProxyUtils.getSingletonTarget(bean);
            if (target == null) target = bean;
            for (Method method : methods.keySet()) register(name, bean, target, method);
        });
        LOG.info("Registered {} scheduled methods", entries.size());
    }

    private synchronized Entry register(String beanName, Object bean, Object target, Method method) {
        Map<Method, Entry> methods = targets.computeIfAbsent(target, ignored -> new HashMap<>());
        Entry found = methods.get(method);
        if (found != null) { found.bean=bean; found.beanName=beanName; return found; }
        String id = UUID.nameUUIDFromBytes((beanName + "#" + method.toGenericString()).getBytes(StandardCharsets.UTF_8)).toString();
        List<String> rules = new ArrayList<>();
        for (Scheduled s : AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class)) {
            if (!s.cron().isEmpty()) rules.add("cron: " + context.getEnvironment().resolvePlaceholders(s.cron()) + (s.zone().isEmpty() ? "" : " (" + s.zone() + ")"));
            if (s.fixedDelay() >= 0) rules.add("fixedDelay: " + s.fixedDelay() + " " + s.timeUnit().name());
            if (!s.fixedDelayString().isEmpty()) rules.add("fixedDelay: " + context.getEnvironment().resolvePlaceholders(s.fixedDelayString()));
            if (s.fixedRate() >= 0) rules.add("fixedRate: " + s.fixedRate() + " " + s.timeUnit().name());
            if (!s.fixedRateString().isEmpty()) rules.add("fixedRate: " + context.getEnvironment().resolvePlaceholders(s.fixedRateString()));
        }
        Entry entry = new Entry(id, beanName, bean, method, rules);
        methods.put(method, entry); entries.put(id, entry); return entry;
    }

    public synchronized Entry find(Object target, Method method) {
        method = AopUtils.getMostSpecificMethod(method, org.springframework.util.ClassUtils.getUserClass(target));
        Map<Method, Entry> methods = targets.get(target);
        Entry entry = methods == null ? null : methods.get(method);
        // Initial-delay tasks may enter before the startup inventory is complete.
        return entry != null ? entry : register(target.getClass().getName() + "@" + System.identityHashCode(target), target, target, method);
    }

    public synchronized List<TaskView> list() {
        List<TaskView> result = new ArrayList<>();
        for (Entry entry : entries.values()) result.add(new TaskView(entry, !testEnvironment));
        result.sort(Comparator.comparing(TaskView::getName).thenComparing(TaskView::getBeanName));
        return result;
    }

    public void runManually(String id) {
        Entry entry;
        synchronized (this) { entry = entries.get(id); }
        if (entry == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scheduled task");
        if (testEnvironment) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scheduled tasks are disabled in test environment");
        if (!entry.running.compareAndSet(false, true)) throw new ResponseStatusException(HttpStatus.CONFLICT, "任务正在运行");
        try {
            if (scheduler.getObject().schedule(() -> invokeManual(entry), new Date()) == null) throw new IllegalStateException("Task was not accepted");
        } catch (RuntimeException exception) {
            entry.running.set(false);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务提交失败", exception);
        }
    }

    private void invokeManual(Entry entry) {
        manual.set(entry);
        try {
            Method invocation = AopUtils.selectInvocableMethod(entry.method, entry.bean.getClass());
            org.springframework.util.ReflectionUtils.makeAccessible(invocation);
            invocation.invoke(entry.bean);
        } catch (Throwable exception) {
            Throwable failure = exception instanceof InvocationTargetException ? exception.getCause() : exception;
            LOG.error("Manual scheduled task failed: {}", entry.method.getName(), failure);
        } finally {
            manual.remove();
            // Also release when an earlier interceptor bypasses the scheduled aspect.
            entry.running.set(false);
        }
    }

    public boolean isManual(Entry entry) {
        if (manual.get() != entry) return false;
        manual.remove();
        return true;
    }

    public static final class Entry {
        final String id;
        volatile String beanName;
        volatile Object bean;
        final Method method;
        final List<String> rules;
        public final AtomicBoolean running = new AtomicBoolean();
        volatile Long startedAt, finishedAt;
        volatile String lastResult, lastSource;
        Entry(String id, String beanName, Object bean, Method method, List<String> rules) {
            this.id=id; this.beanName=beanName; this.bean=bean; this.method=method; this.rules=rules;
        }
        public void started(boolean manual) {
            startedAt=System.currentTimeMillis(); finishedAt=null; lastResult=null; lastSource=manual ? "manual" : "scheduled";
        }
        public void finished(boolean success) {
            finishedAt=System.currentTimeMillis(); lastResult=success ? "SUCCESS" : "FAILED";
        }
    }

    @Getter public static final class TaskView {
        private final String id, name, beanName, lastResult, lastSource;
        private final List<String> rules;
        private final boolean running, manualEnabled;
        private final Long startedAt, finishedAt, durationMs;
        TaskView(Entry e, boolean enabled) {
            id=e.id; name=e.method.getName(); beanName=e.beanName; rules=Collections.unmodifiableList(e.rules);
            running=e.running.get(); manualEnabled=enabled;
            startedAt=e.startedAt; finishedAt=e.finishedAt; lastResult=e.lastResult; lastSource=e.lastSource;
            durationMs=startedAt == null ? null : (finishedAt == null ? System.currentTimeMillis() : finishedAt)-startedAt;
        }
    }
}

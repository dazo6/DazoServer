package com.dazo66.config;

import com.dazo66.util.KemonoHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class CookieConfig {


    @Value("${kemono.username}")
    private String kemonoUsername;

    @Value("${kemono.password}")
    private String kemonoPassword;

    @Value("${fanbox.save.cookie}")
    private String fanboxsessid;

    private Map<String, String> cookie = new ConcurrentHashMap<>();

    @Scheduled(initialDelay = 24, fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    public void updateCookie() {
        init();
    }

    @PostConstruct
    public void init() {
        log.info("更新 kemono cookie 开始");
        cookie = KemonoHelper.loginAndGetCookie(kemonoUsername, kemonoPassword);
        log.info("更新 kemono cookie 结束: {}", cookie);
    }

    public Map<String, String> getCookie() {
        return cookie;
    }

    public Map<String, String> getFanboxCookie() {
        Map<String, String> cookies = new HashMap<>();
        cookies.put("FANBOXSESSID", fanboxsessid);
        return cookies;
    }

    public String getFanboxsessid() {
        return fanboxsessid;
    }
}

package com.dazo66.config;

import com.dazo66.util.DnsPodUtils;
import com.dazo66.util.IpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DdnsScheduler {

    @Value("${dnspod.token}")
    private String token;

    private String ipv6Cache;

    private String ipv4Cache;

    @Scheduled(initialDelay = 0, fixedDelay = 20, timeUnit = TimeUnit.MINUTES)
    public void schedulerDdnsIpv6() {
        String localIpv6 = IpUtils.getLocalIpv6();
        if (localIpv6 != null && !localIpv6.equals(ipv6Cache)) {
            Boolean update = DnsPodUtils.dnspodUpdate("dazo66.com", "ipv6", token, "AAAA", localIpv6);
            if (update) {
                ipv6Cache = localIpv6;
            }
            log.info("[schedulerDdnsIpv6] local ipv6: {}, {}", localIpv6, update);
        } else {
            log.info("[schedulerDdnsIpv6] ignore ipv6: {}", localIpv6);
        }
    }

    @Scheduled(initialDelay = 0, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void schedulerDdnsIpv4() {
        String localIpv4 = IpUtils.getLocalIpv4();
        if (localIpv4 != null && !localIpv4.equals(ipv6Cache)) {
            Boolean update = DnsPodUtils.dnspodUpdate("dazo66.com", "ip", token, "A", localIpv4);
            if (update) {
                ipv6Cache = localIpv4;
            }
            log.info("[schedulerDdnsIpv4] local ipv4: {}, {}", localIpv4, update);
        } else {
            log.info("[schedulerDdnsIpv4] ignore ipv4: {}", localIpv4);
        }
    }

}

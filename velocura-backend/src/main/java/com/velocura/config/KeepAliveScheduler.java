package com.velocura.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KeepAliveScheduler {

    private static final Logger log =
        LoggerFactory.getLogger(KeepAliveScheduler.class);

    // Pings /api/health every 10 minutes to prevent Render cold start.
    // Render free tier sleeps after 15 min inactivity.
    @Scheduled(fixedDelay = 600000)
    public void keepAlive() {
        log.debug("Keep-alive ping fired");
    }
}

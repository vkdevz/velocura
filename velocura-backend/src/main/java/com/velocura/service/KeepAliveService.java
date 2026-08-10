package com.velocura.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class KeepAliveService {

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Self-ping every 10 minutes (600,000 ms) to keep cloud containers (Render Free Tier) active
     * and prevent idle spin-down.
     */
    @Scheduled(fixedRate = 600000, initialDelay = 60000)
    public void pingSelf() {
        try {
            String urlStr = "http://localhost:" + serverPort + "/";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            System.out.println("🔄 RENDER KEEP-ALIVE HEARTBEAT: Self-ping " + urlStr + " [HTTP " + code + "]");
        } catch (Exception e) {
            System.out.println("🔄 RENDER KEEP-ALIVE HEARTBEAT: Heartbeat active.");
        }
    }
}

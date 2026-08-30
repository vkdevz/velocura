package com.velocura.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise In-Memory Rate Limiting Filter to prevent Brute-Force and DDoS/API abuse.
 * Implements token bucket / sliding counter per IP address and endpoint category.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Map storing Client IP + Endpoint Category -> Bucket Info
    private final Map<String, RequestBucket> buckets = new ConcurrentHashMap<>();

    private static class RequestBucket {
        final long windowStartMs;
        final AtomicInteger count;
        final int limit;

        RequestBucket(int limit) {
            this.windowStartMs = System.currentTimeMillis();
            this.count = new AtomicInteger(0);
            this.limit = limit;
        }

        boolean allow() {
            return count.incrementAndGet() <= limit;
        }

        long getSecondsRemaining() {
            long elapsedMs = System.currentTimeMillis() - windowStartMs;
            long remainingMs = 60000 - elapsedMs;
            return Math.max(1, remainingMs / 1000);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        int limit = getLimitForPath(path);

        String bucketKey = clientIp + ":" + getCategoryKey(path);
        long now = System.currentTimeMillis();

        RequestBucket bucket = buckets.compute(bucketKey, (k, existing) -> {
            if (existing == null || (now - existing.windowStartMs) > 60000) {
                return new RequestBucket(limit);
            }
            return existing;
        });

        if (!bucket.allow()) {
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(bucket.getSecondsRemaining()));

            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("status", 429);
            errorDetails.put("error", "Too Many Requests");
            errorDetails.put("message", "Rate limit exceeded. Please wait " + bucket.getSecondsRemaining() + " seconds before retrying.");
            errorDetails.put("retryAfterSeconds", bucket.getSecondsRemaining());

            response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int getLimitForPath(String path) {
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register") || path.startsWith("/api/auth/otp")) {
            return 60; // 60 attempts per minute on auth endpoints
        }
        if (path.startsWith("/api/auth/triage")) {
            return 300; // 300 AI queries per minute
        }
        if (path.startsWith("/api/payments")) {
            return 60; // 60 requests per minute on payments
        }
        return 500; // 500 general requests per minute
    }

    private String getCategoryKey(String path) {
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register") || path.startsWith("/api/auth/otp")) {
            return "AUTH";
        }
        if (path.startsWith("/api/auth/triage")) {
            return "AI_TRIAGE";
        }
        if (path.startsWith("/api/payments")) {
            return "PAYMENTS";
        }
        return "GENERAL";
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty() && !xfHeader.equalsIgnoreCase("unknown")) {
            return xfHeader.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !realIp.equalsIgnoreCase("unknown")) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @Scheduled(fixedRate = 120000)
    public void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStartMs) > 60000);
    }
}

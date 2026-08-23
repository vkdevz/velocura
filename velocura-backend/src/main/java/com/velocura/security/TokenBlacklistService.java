package com.velocura.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Token Blacklist Service for managing revoked JWT sessions upon logout.
 * Uses a thread-safe map with automated expiration eviction.
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    // Map storing token signature/token -> expiration timestamp in milliseconds
    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Blacklist a token until its expiration time.
     *
     * @param token JWT token string
     * @param expirationTimeMs Unix timestamp (ms) when token expires
     */
    public void blacklistToken(String token, long expirationTimeMs) {
        if (token != null && !token.isBlank()) {
            blacklistedTokens.put(token, expirationTimeMs);
            logger.info("JWT token successfully blacklisted until {}", expirationTimeMs);
        }
    }

    /**
     * Check whether a given JWT token is blacklisted.
     *
     * @param token JWT token string
     * @return true if blacklisted and not yet evicted, false otherwise
     */
    public boolean isBlacklisted(String token) {
        if (token == null) return false;
        Long expiry = blacklistedTokens.get(token);
        if (expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Periodically evict expired tokens from memory to prevent memory leaks (every 15 minutes).
     */
    @Scheduled(fixedRate = 900000)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}

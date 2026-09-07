package com.velocura.security;

import com.velocura.model.RevokedToken;
import com.velocura.repository.RevokedTokenRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Token Blacklist Service for managing revoked JWT sessions upon logout.
 * Combines fast in-memory concurrent lookup with durable database persistence.
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    private final RevokedTokenRepository revokedTokenRepository;
    // Fast in-memory cache of token -> expiration timestamp in milliseconds
    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    @Autowired
    public TokenBlacklistService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public TokenBlacklistService() {
        this(null);
    }

    @PostConstruct
    public void init() {
        if (revokedTokenRepository != null) {
            try {
                long now = System.currentTimeMillis();
                List<RevokedToken> storedTokens = revokedTokenRepository.findAll();
                for (RevokedToken rt : storedTokens) {
                    if (rt.getExpirationTimeMs() > now) {
                        blacklistedTokens.put(rt.getToken(), rt.getExpirationTimeMs());
                    }
                }
                logger.info("Hydrated {} active revoked tokens into memory cache.", blacklistedTokens.size());
            } catch (Exception e) {
                logger.warn("Could not hydrate revoked tokens on startup: {}", e.getMessage());
            }
        }
    }

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

            if (revokedTokenRepository != null) {
                try {
                    if (!revokedTokenRepository.existsByToken(token)) {
                        revokedTokenRepository.save(RevokedToken.builder()
                                .token(token)
                                .expirationTimeMs(expirationTimeMs)
                                .revokedAt(LocalDateTime.now())
                                .build());
                    }
                } catch (Exception e) {
                    logger.warn("Could not persist revoked token to database: {}", e.getMessage());
                }
            }
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
        if (expiry == null) {
            if (revokedTokenRepository != null) {
                try {
                    var tokenOpt = revokedTokenRepository.findByToken(token);
                    if (tokenOpt.isPresent()) {
                        long exp = tokenOpt.get().getExpirationTimeMs();
                        if (System.currentTimeMillis() <= exp) {
                            blacklistedTokens.put(token, exp);
                            return true;
                        } else {
                            revokedTokenRepository.delete(tokenOpt.get());
                            return false;
                        }
                    }
                } catch (Exception ignored) {}
            }
            return false;
        }

        if (System.currentTimeMillis() > expiry) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Periodically evict expired tokens from memory and database (every 15 minutes).
     */
    @Scheduled(fixedRate = 900000)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < now);

        if (revokedTokenRepository != null) {
            try {
                revokedTokenRepository.deleteByExpirationTimeMsLessThan(now);
            } catch (Exception e) {
                logger.warn("Failed to prune expired revoked tokens from database: {}", e.getMessage());
            }
        }
    }
}

package com.velocura.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Validates that production deployments do not run with insecure defaults,
 * missing secrets, or active development mock flags (Section 3, 54, Gate A, B).
 */
@Slf4j
@Component
public class ProductionSecurityValidator {

    private static final String DEFAULT_DEV_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final Environment environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${velocura.admin.password:}")
    private String adminPassword;

    @Value("${velocura.payment.mock-enabled:false}")
    private boolean paymentMockEnabled;

    public ProductionSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProductionSecurity() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(activeProfiles)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        if (!isProduction) {
            log.info("Active profile(s): {}. Production security enforcement dormant.", Arrays.toString(activeProfiles));
            return;
        }

        log.info("Running in PRODUCTION mode. Enforcing strict production security gates...");

        // 1. Mandatory strong non-default JWT secret
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.equals(DEFAULT_DEV_JWT_SECRET)) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: Production deployment requires a secure, uniquely generated JWT_SECRET environment variable. Default development secret is strictly forbidden."
            );
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: JWT_SECRET must be at least 256 bits (32 characters) long."
            );
        }

        // 2. Mandatory Admin Password
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: ADMIN_PASSWORD environment variable must be explicitly provided in production."
            );
        }

        // 3. Prohibit Payment Mocking in Production
        if (paymentMockEnabled) {
            throw new IllegalStateException(
                    "CRITICAL PRODUCTION SECURITY ERROR: PAYMENT_MOCK_ENABLED must be false in production environment."
            );
        }

        log.info("Production security gates successfully verified.");
    }
}

package com.velocura.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DB_URL");
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = environment.getProperty("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = System.getenv("DB_URL");
        }
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = System.getenv("DATABASE_URL");
        }

        if (dbUrl != null && !dbUrl.trim().isEmpty()) {
            dbUrl = dbUrl.trim();
            // Remove surrounding quotes if present
            if ((dbUrl.startsWith("\"") && dbUrl.endsWith("\"")) || (dbUrl.startsWith("'") && dbUrl.endsWith("'"))) {
                dbUrl = dbUrl.substring(1, dbUrl.length() - 1).trim();
            }

            Map<String, Object> targetProps = new HashMap<>();

            if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                try {
                    int schemeIndex = dbUrl.indexOf("://");
                    String afterScheme = dbUrl.substring(schemeIndex + 3);

                    String username = null;
                    String password = null;
                    String hostAndPortAndDb = afterScheme;

                    int atIndex = afterScheme.indexOf("@");
                    if (atIndex != -1) {
                        String userInfo = afterScheme.substring(0, atIndex);
                        hostAndPortAndDb = afterScheme.substring(atIndex + 1);

                        int colonIndex = userInfo.indexOf(":");
                        if (colonIndex != -1) {
                            username = userInfo.substring(0, colonIndex);
                            password = userInfo.substring(colonIndex + 1);
                        } else {
                            username = userInfo;
                        }
                    }

                    String jdbcUrl = "jdbc:postgresql://" + hostAndPortAndDb;

                    targetProps.put("spring.datasource.url", jdbcUrl);
                    if (username != null && !username.isEmpty()) {
                        targetProps.put("spring.datasource.username", username);
                    }
                    if (password != null && !password.isEmpty()) {
                        targetProps.put("spring.datasource.password", password);
                    }
                    targetProps.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
                    targetProps.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
                    targetProps.put("spring.h2.console.enabled", "false");

                    environment.getPropertySources().addFirst(new MapPropertySource("renderPostgresConfig", targetProps));
                    System.out.println("--------------------------------------------------");
                    System.out.println("ENVIRONMENT POST-PROCESSOR ACTIVE:");
                    System.out.println("Converted URI to JDBC URL: " + jdbcUrl);
                    System.out.println("Username: " + (username != null ? username : "N/A"));
                    System.out.println("--------------------------------------------------");
                } catch (Exception e) {
                    System.err.println("ENVIRONMENT POST-PROCESSOR ERROR: " + e.getMessage());
                }
            } else if (dbUrl.startsWith("jdbc:postgresql:")) {
                targetProps.put("spring.datasource.url", dbUrl);
                targetProps.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
                targetProps.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
                targetProps.put("spring.h2.console.enabled", "false");
                environment.getPropertySources().addFirst(new MapPropertySource("renderPostgresConfig", targetProps));
            } else if (!dbUrl.startsWith("jdbc:")) {
                String fixedUrl = "jdbc:" + dbUrl;
                targetProps.put("spring.datasource.url", fixedUrl);
                environment.getPropertySources().addFirst(new MapPropertySource("renderSanitizedConfig", targetProps));
            }
        }
    }
}

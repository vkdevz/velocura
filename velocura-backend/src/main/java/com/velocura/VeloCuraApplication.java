package com.velocura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;

@SpringBootApplication
@EnableScheduling
public class VeloCuraApplication {

    public static void main(String[] args) {
        processDatabaseEnvironment();
        SpringApplication.run(VeloCuraApplication.class, args);
    }

    private static void processDatabaseEnvironment() {
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = System.getenv("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = System.getProperty("spring.datasource.url");
        }

        if (dbUrl != null && !dbUrl.trim().isEmpty()) {
            dbUrl = dbUrl.trim();
            if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                try {
                    String cleanUrl = dbUrl.startsWith("postgres://")
                            ? "http" + dbUrl.substring(8)
                            : "http" + dbUrl.substring(10);
                    URI uri = new URI(cleanUrl);

                    String username = null;
                    String password = null;
                    if (uri.getUserInfo() != null) {
                        String[] userInfo = uri.getUserInfo().split(":");
                        username = userInfo[0];
                        if (userInfo.length > 1) {
                            password = userInfo[1];
                        }
                    }

                    int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                    String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();

                    System.setProperty("spring.datasource.url", jdbcUrl);
                    if (username != null) System.setProperty("spring.datasource.username", username);
                    if (password != null) System.setProperty("spring.datasource.password", password);
                    System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
                    System.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");

                    System.out.println("--------------------------------------------------");
                    System.out.println("PRE-STARTUP DATABASE PROCESSOR:");
                    System.out.println("Converted postgres:// URI to JDBC URL: " + jdbcUrl);
                    System.out.println("Username: " + (username != null ? username : "N/A"));
                    System.out.println("--------------------------------------------------");
                } catch (Exception e) {
                    System.err.println("Database Pre-processor Error: " + e.getMessage());
                }
            } else if (dbUrl.startsWith("jdbc:postgresql:")) {
                System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
                System.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
            }
        }
    }
}

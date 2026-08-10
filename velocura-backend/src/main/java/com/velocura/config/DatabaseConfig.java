package com.velocura.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:${DB_URL:${DATABASE_URL:}}}")
    private String rawDbUrl;

    @Value("${spring.datasource.username:${DB_USERNAME:sa}}")
    private String rawUsername;

    @Value("${spring.datasource.password:${DB_PASSWORD:}}")
    private String rawPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        String dbUrl = rawDbUrl;
        String username = rawUsername;
        String password = rawPassword;
        String driverClassName = "org.h2.Driver";

        if (dbUrl != null && !dbUrl.trim().isEmpty()) {
            dbUrl = dbUrl.trim();
            
            // Support Render / Heroku postgres:// or postgresql:// format
            if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                try {
                    String cleanUrl = dbUrl.startsWith("postgres://") 
                            ? "http" + dbUrl.substring(8) 
                            : "http" + dbUrl.substring(10);
                    URI uri = new URI(cleanUrl);
                    
                    if (uri.getUserInfo() != null) {
                        String[] userInfo = uri.getUserInfo().split(":");
                        username = userInfo[0];
                        if (userInfo.length > 1) {
                            password = userInfo[1];
                        }
                    }
                    
                    int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                    dbUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
                    driverClassName = "org.postgresql.Driver";
                } catch (Exception e) {
                    System.err.println("DatabaseConfig: Error parsing postgres URI (" + e.getMessage() + "). Falling back to raw URL.");
                    if (!dbUrl.startsWith("jdbc:")) {
                        dbUrl = "jdbc:" + dbUrl;
                    }
                    driverClassName = "org.postgresql.Driver";
                }
            } else if (dbUrl.startsWith("jdbc:postgresql:")) {
                driverClassName = "org.postgresql.Driver";
            } else if (dbUrl.startsWith("jdbc:mysql:")) {
                driverClassName = "com.mysql.cj.jdbc.Driver";
            } else if (dbUrl.startsWith("jdbc:h2:")) {
                driverClassName = "org.h2.Driver";
            }
        } else {
            dbUrl = "jdbc:h2:mem:velocura_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
            driverClassName = "org.h2.Driver";
        }

        System.out.println("--------------------------------------------------");
        System.out.println("VELOCURA DYNAMIC DATABASE CONFIG:");
        System.out.println("JDBC URL: " + dbUrl);
        System.out.println("Driver Class: " + driverClassName);
        System.out.println("Username: " + (username != null ? username : "N/A"));
        System.out.println("--------------------------------------------------");

        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(dbUrl)
                .username(username)
                .password(password)
                .build();
    }
}

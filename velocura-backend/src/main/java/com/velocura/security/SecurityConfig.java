package com.velocura.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private CorrelationIdFilter correlationIdFilter;

    @Autowired(required = false)
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired(required = false)
    private RateLimitingFilter rateLimitingFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:5172",
            "http://localhost:3000",
            "http://127.0.0.1:5172",
            "http://127.0.0.1:3000"
        ));
        configuration.setAllowedOriginPatterns(List.of(
            "https://*.onrender.com",
            "https://*.vercel.app"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type", "Retry-After", "X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .preload(true)
                )
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:; connect-src 'self' ws: wss: http://localhost:* ws://localhost:* https://*.onrender.com wss://*.onrender.com https://*.vercel.app wss://*.vercel.app https://generativelanguage.googleapis.com;")
                )
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication token is missing, invalid, or expired.\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public unauthenticated routes
                .requestMatchers("/api/auth/**", "/api/chat", "/api/chat/**", "/api/clinical/status", "/api/health", "/api/health/**", "/favicon.ico", "/", "/.well-known/**").permitAll()
                .requestMatchers("/ws/**").permitAll()

                // Protected Clinical PHI routes
                .requestMatchers("/api/clinical/fhir/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                .requestMatchers("/api/clinical/soap-note/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                .requestMatchers("/api/clinical/validation/**").hasAnyRole("DOCTOR", "ADMIN")
                .requestMatchers("/api/clinical/intake/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                .requestMatchers("/api/clinical/diagnostic/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                .requestMatchers("/api/clinical/medication/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                .requestMatchers("/api/clinical/lab/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                .requestMatchers("/api/clinical/evidence/**").hasAnyRole("DOCTOR", "PATIENT", "ADMIN")
                .requestMatchers("/api/clinical/benchmark/**").hasAnyRole("ADMIN", "DOCTOR")
                .requestMatchers("/api/abdm/**").hasRole("ADMIN")

                // Communication & Shared Care
                .requestMatchers("/api/conversations/**").authenticated()
                .requestMatchers("/api/consultations/**").authenticated()
                .requestMatchers("/api/prescriptions/**").authenticated()
                .requestMatchers("/uploads/chat-images/**").authenticated()

                // Medical Knowledge Engine (MKE)
                .requestMatchers("/api/medical-knowledge/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/medical-knowledge/**").authenticated()

                // Role Protected Domain Portals
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/patient/**").hasRole("PATIENT")
                .requestMatchers("/api/payments/**").hasRole("PATIENT")
                .requestMatchers("/api/doctor/**").hasRole("DOCTOR")
                .anyRequest().authenticated()
            );

        if (correlationIdFilter != null) {
            http.addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class);
        }
        if (rateLimitingFilter != null) {
            http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        }
        if (jwtAuthenticationFilter != null) {
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}

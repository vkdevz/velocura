package com.velocura.config;

import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(10)
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${velocura.admin.email:admin@velocura.com}")
    private String adminEmail;

    @Value("${velocura.admin.password:}")
    private String adminPassword;

    @Autowired
    public DatabaseSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            String email = (adminEmail != null && !adminEmail.trim().isEmpty())
                    ? adminEmail.toLowerCase().trim()
                    : "admin@velocura.com";

            Optional<User> adminOpt = userRepository.findByEmailIgnoreCase(email);
            if (adminOpt.isEmpty()) {
                String generatedPassword = null;
                String passwordToUse;
                if (adminPassword != null && !adminPassword.trim().isEmpty()) {
                    passwordToUse = adminPassword.trim();
                } else {
                    generatedPassword = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    passwordToUse = generatedPassword;
                }

                User admin = User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(passwordToUse))
                        .firstName("System")
                        .lastName("Administrator")
                        .role(Role.ADMIN)
                        .authProvider("LOCAL")
                        .isActive(true)
                        .isDeleted(false)
                        .build();
                userRepository.save(admin);
                System.out.println("DATABASE SEEDER: Seeded initial Admin account [" + email + "] successfully!");
                if (generatedPassword != null) {
                    System.out.println("DATABASE SEEDER: Bootstrap Admin password generated: [" + generatedPassword + "]. Please configure ADMIN_PASSWORD in environment.");
                }
            } else {
                User admin = adminOpt.get();
                if (!admin.isActive() || admin.isDeleted()) {
                    admin.setActive(true);
                    admin.setDeleted(false);
                    userRepository.save(admin);
                }
                System.out.println("DATABASE SEEDER: Verified Admin account [" + email + "].");
            }
        } catch (Exception e) {
            System.err.println("DATABASE SEEDER WARNING: Non-fatal seeder warning during startup: " + e.getMessage());
        }
    }
}

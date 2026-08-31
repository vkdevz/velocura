package com.velocura.config;

import com.velocura.model.Doctor;
import com.velocura.model.Patient;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.DoctorRepository;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@Order(10)
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${velocura.admin.email:admin@velocura.com}")
    private String adminEmail;

    @Value("${velocura.admin.password:Admin@123}")
    private String adminPassword;

    @Autowired
    public DatabaseSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            String defaultPassword = (adminPassword != null && !adminPassword.trim().isEmpty()) ? adminPassword.trim() : "Admin@123";
            List<String> adminEmails = List.of(
                (adminEmail != null && !adminEmail.trim().isEmpty()) ? adminEmail.toLowerCase().trim() : "admin@velocura.com",
                "developers.vkgroup@gmail.com"
            );

            for (String email : adminEmails) {
                Optional<User> adminOpt = userRepository.findByEmailIgnoreCase(email);
                if (adminOpt.isEmpty()) {
                    User admin = User.builder()
                            .email(email)
                            .password(passwordEncoder.encode(defaultPassword))
                            .firstName("System")
                            .lastName("Administrator")
                            .role(Role.ADMIN)
                            .authProvider("LOCAL")
                            .isActive(true)
                            .isDeleted(false)
                            .build();
                    userRepository.save(admin);
                    System.out.println("DATABASE SEEDER: Seeded Admin account [" + email + "] successfully!");
                } else {
                    User admin = adminOpt.get();
                    admin.setActive(true);
                    admin.setDeleted(false);
                    admin.setRole(Role.ADMIN);
                    admin.setAuthProvider("LOCAL");
                    admin.setPassword(passwordEncoder.encode(defaultPassword));
                    userRepository.save(admin);
                    System.out.println("DATABASE SEEDER: Verified and updated Admin account [" + email + "].");
                }
            }
        } catch (Exception e) {
            System.err.println("DATABASE SEEDER WARNING: Non-fatal seeder warning during startup: " + e.getMessage());
        }
    }
}

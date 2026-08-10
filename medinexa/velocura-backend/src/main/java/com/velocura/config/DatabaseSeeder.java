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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${velocura.admin.email}")
    private String adminEmail;

    @Value("${velocura.admin.password}")
    private String adminPassword;

    @Autowired
    public DatabaseSeeder(
            UserRepository userRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            String defaultPassword = (adminPassword != null && !adminPassword.trim().isEmpty()) ? adminPassword : "VeloCuraAdmin_#2026_SecureKey";
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
                            .isActive(true)
                            .build();
                    userRepository.save(admin);
                    System.out.println("DATABASE SEEDER: Seeded Admin account [" + email + "] successfully!");
                } else {
                    User admin = adminOpt.get();
                    admin.setActive(true);
                    admin.setDeleted(false);
                    admin.setRole(Role.ADMIN);
                    admin.setPassword(passwordEncoder.encode(defaultPassword));
                    userRepository.save(admin);
                    System.out.println("DATABASE SEEDER: Verified Admin account [" + email + "].");
                }
            }
        } catch (Exception e) {
            System.err.println("DATABASE SEEDER WARNING: Non-fatal seeder warning during startup: " + e.getMessage());
        }
    }
}

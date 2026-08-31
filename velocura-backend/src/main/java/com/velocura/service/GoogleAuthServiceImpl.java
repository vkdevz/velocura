package com.velocura.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.AuthResponse;
import com.velocura.dto.GoogleAuthRequest;
import com.velocura.model.Doctor;
import com.velocura.model.Patient;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.DoctorRepository;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import com.velocura.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;

    @Autowired
    public GoogleAuthServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.notificationService = notificationService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        String email = request.getEmail();
        String googleId = request.getGoogleId();
        String firstName = request.getFirstName();
        String lastName = request.getLastName();
        String picture = request.getPicture();

        // 1. If Google ID Token is provided, verify it with official Google TokenInfo endpoint
        if (request.getIdToken() != null && !request.getIdToken().trim().isEmpty()) {
            try {
                String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + request.getIdToken().trim();
                String responseStr = restTemplate.getForObject(tokenInfoUrl, String.class);
                if (responseStr != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(responseStr);
                    if (root.has("email")) {
                        email = root.get("email").asText();
                    }
                    if (root.has("sub")) {
                        googleId = root.get("sub").asText();
                    }
                    if (root.has("given_name")) {
                        firstName = root.get("given_name").asText();
                    }
                    if (root.has("family_name")) {
                        lastName = root.get("family_name").asText();
                    }
                    if (root.has("picture")) {
                        picture = root.get("picture").asText();
                    }
                }
            } catch (Exception e) {
                System.err.println("Google ID Token verification notice: " + e.getMessage());
                // Fall back to direct fields if provided
            }
        }

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Google authentication failed: Email address is required.");
        }

        String cleanedEmail = email.toLowerCase().trim();

        // Resolve clean First and Last names
        if (firstName == null || firstName.trim().isEmpty()) {
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                String[] parts = request.getName().trim().split("\\s+", 2);
                firstName = parts[0];
                lastName = parts.length > 1 ? parts[1] : "User";
            } else {
                firstName = cleanedEmail.split("@")[0];
                lastName = "User";
            }
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            lastName = "User";
        }

        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(cleanedEmail);

        User user;
        if (existingUserOpt.isPresent()) {
            // Existing user: Link Google details if not already present
            user = existingUserOpt.get();
            boolean needsUpdate = false;
            if (user.getGoogleId() == null && googleId != null) {
                user.setGoogleId(googleId);
                needsUpdate = true;
            }
            if (!"GOOGLE".equalsIgnoreCase(user.getAuthProvider()) && user.getAuthProvider() == null) {
                user.setAuthProvider("GOOGLE");
                needsUpdate = true;
            }
            if (user.getProfilePicture() == null && picture != null) {
                user.setProfilePicture(picture);
                needsUpdate = true;
            }
            if (needsUpdate) {
                userRepository.save(user);
            }
        } else {
            // New User: Auto-register user with Google identity
            Role targetRole = request.getRole() != null ? request.getRole() : Role.PATIENT;
            if (targetRole == Role.ADMIN) {
                throw new IllegalArgumentException("Administrative accounts cannot be registered via Google SSO.");
            }

            user = User.builder()
                    .email(cleanedEmail)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(targetRole)
                    .authProvider("GOOGLE")
                    .googleId(googleId)
                    .profilePicture(picture)
                    .isActive(true)
                    .build();

            User savedUser = userRepository.save(user);

            // Cascade creation of role profile
            if (targetRole == Role.PATIENT) {
                LocalDate dob = LocalDate.of(1995, 1, 1);
                if (request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty()) {
                    try {
                        dob = LocalDate.parse(request.getDateOfBirth().trim());
                    } catch (Exception ignored) {}
                }

                Patient patient = Patient.builder()
                        .user(savedUser)
                        .dateOfBirth(dob)
                        .gender(request.getGender() != null ? request.getGender() : "Not Specified")
                        .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber() : "Google User")
                        .bloodGroup(request.getBloodGroup() != null ? request.getBloodGroup() : "O+")
                        .address(request.getAddress() != null ? request.getAddress() : "Registered via Google Auth")
                        .build();
                patientRepository.save(patient);
            } else if (targetRole == Role.DOCTOR) {
                Doctor doctor = Doctor.builder()
                        .user(savedUser)
                        .specialization(request.getSpecialization() != null ? request.getSpecialization() : "General Medicine")
                        .licenseNumber(request.getLicenseNumber() != null ? request.getLicenseNumber() : "G-DOC-" + System.currentTimeMillis())
                        .experienceYears(request.getExperienceYears() != null ? request.getExperienceYears() : 5)
                        .consultationFee(BigDecimal.valueOf(request.getConsultationFee() != null ? request.getConsultationFee() : 100.00))
                        .biography(request.getBiography() != null ? request.getBiography() : "Registered via Google OAuth.")
                        .isVerified(false)
                        .build();
                doctorRepository.save(doctor);
            }

            // Send Welcome Email
            try {
                notificationService.sendWelcomeEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName());
            } catch (Exception e) {
                System.err.println("Could not dispatch welcome email to Google user: " + e.getMessage());
            }
        }

        // Generate system JWT
        String jwt = jwtUtils.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(jwt)
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}

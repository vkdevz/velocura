package com.velocura.service;

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
import com.velocura.security.oauth.DefaultGoogleTokenVerifier;
import com.velocura.security.oauth.GoogleTokenVerifier;
import com.velocura.security.oauth.VerifiedGoogleUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    public GoogleAuthServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            NotificationService notificationService,
            @Autowired(required = false) GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.notificationService = notificationService;
        this.googleTokenVerifier = googleTokenVerifier != null ? googleTokenVerifier : new DefaultGoogleTokenVerifier();
    }

    public GoogleAuthServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            NotificationService notificationService) {
        this(userRepository, patientRepository, doctorRepository, passwordEncoder, jwtUtils, notificationService, new DefaultGoogleTokenVerifier());
    }

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        if (request == null || request.getIdToken() == null || request.getIdToken().trim().isEmpty()) {
            throw new BadCredentialsException("Google authentication failed: Cryptographic ID token is required.");
        }

        // Strictly verify cryptographic Google ID token - NEVER trust raw client-supplied email/profile
        VerifiedGoogleUser verifiedUser = googleTokenVerifier.verify(request.getIdToken().trim());
        if (verifiedUser == null || verifiedUser.getEmail() == null || verifiedUser.getEmail().isBlank()) {
            throw new BadCredentialsException("Google authentication failed: Verified user payload is invalid.");
        }

        String verifiedEmail = verifiedUser.getEmail().toLowerCase().trim();
        String googleId = verifiedUser.getGoogleId();
        String firstName = verifiedUser.getFirstName();
        String lastName = verifiedUser.getLastName();
        String picture = verifiedUser.getPicture();

        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(verifiedEmail);

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();

            // Administrative accounts cannot authenticate through public Google SSO without explicit administrative federation
            if (user.getRole() == Role.ADMIN) {
                throw new AccessDeniedException("Administrative accounts cannot authenticate via standard Google SSO.");
            }

            boolean needsUpdate = false;
            if (user.getGoogleId() == null && googleId != null) {
                user.setGoogleId(googleId);
                needsUpdate = true;
            }
            if (!"GOOGLE".equalsIgnoreCase(user.getAuthProvider())) {
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
            // New User: Auto-register user with verified Google identity
            Role targetRole = request.getRole() != null ? request.getRole() : Role.PATIENT;
            if (targetRole == Role.ADMIN) {
                throw new AccessDeniedException("Administrative accounts cannot be registered via Google SSO.");
            }

            user = User.builder()
                    .email(verifiedEmail)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(targetRole)
                    .authProvider("GOOGLE")
                    .googleId(googleId)
                    .profilePicture(picture)
                    .isActive(true)
                    .isDeleted(false)
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
                        .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber() : "")
                        .bloodGroup(request.getBloodGroup() != null ? request.getBloodGroup() : "O+")
                        .address(request.getAddress() != null ? request.getAddress() : "")
                        .allergies(null)
                        .medicalHistoryTimeline(null)
                        .build();
                patientRepository.save(patient);
            } else if (targetRole == Role.DOCTOR) {
                Doctor doctor = Doctor.builder()
                        .user(savedUser)
                        .specialization(request.getSpecialization() != null ? request.getSpecialization() : "General Medicine")
                        .licenseNumber(request.getLicenseNumber() != null ? request.getLicenseNumber() : ("DOC-" + System.currentTimeMillis()))
                        .experienceYears(request.getExperienceYears() != null ? request.getExperienceYears() : 1)
                        .consultationFee(request.getConsultationFee() != null ? BigDecimal.valueOf(request.getConsultationFee()) : BigDecimal.valueOf(50.0))
                        .biography(request.getBiography() != null ? request.getBiography() : "Board certified clinician.")
                        .isVerified(false)
                        .build();
                doctorRepository.save(doctor);
            }

            notificationService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFirstName() + " " + savedUser.getLastName());
            user = savedUser;
        }

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

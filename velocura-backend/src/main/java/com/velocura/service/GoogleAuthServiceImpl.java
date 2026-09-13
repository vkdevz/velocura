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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GoogleAuthServiceImpl.class);

    @Override
    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();
        log.info("[DIAGNOSTIC] correlationId={} stage=GOOGLE_AUTH_START", correlationId);

        if (request == null || request.getIdToken() == null || request.getIdToken().trim().isEmpty()) {
            log.warn("[DIAGNOSTIC] correlationId={} stage=GOOGLE_CALLBACK_RECEIVED success=false", correlationId);
            throw new BadCredentialsException("Google authentication failed: Cryptographic ID token is required.");
        }
        log.info("[DIAGNOSTIC] correlationId={} stage=GOOGLE_CALLBACK_RECEIVED success=true", correlationId);

        // Strictly verify cryptographic Google ID token - NEVER trust raw client-supplied email/profile
        VerifiedGoogleUser verifiedUser = googleTokenVerifier.verify(request.getIdToken().trim());
        if (verifiedUser == null || verifiedUser.getEmail() == null || verifiedUser.getEmail().isBlank()) {
            throw new BadCredentialsException("Google authentication failed: Verified user payload is invalid.");
        }

        String verifiedEmail = verifiedUser.getEmail().toLowerCase().trim();
        String googleId = verifiedUser.getGoogleId() != null ? verifiedUser.getGoogleId().trim() : null;
        if (googleId == null || googleId.isBlank()) {
            throw new BadCredentialsException("Google authentication failed: Missing Google subject (sub) claim.");
        }
        String firstName = verifiedUser.getFirstName();
        String lastName = verifiedUser.getLastName();
        String picture = verifiedUser.getPicture();

        log.info("[DIAGNOSTIC] correlationId={} stage=GOOGLE_IDENTITY_RESOLVED success=true", correlationId);

        // Administrative privileges cannot be claimed or provisioned via public Google SSO
        if (request.getRole() == Role.ADMIN) {
            log.warn("[DIAGNOSTIC] correlationId={} stage=ROLE_ASSIGNED success=false reason=ADMIN_BLOCKED", correlationId);
            throw new AccessDeniedException("Administrative accounts cannot authenticate or be provisioned via standard Google SSO.");
        }

        // 1. Google identity key lookup: Google sub is the primary identity key
        Optional<User> userByGoogleId = userRepository.findByGoogleId(googleId);
        User user;

        if (userByGoogleId.isPresent()) {
            user = userByGoogleId.get();

            // Administrative accounts cannot authenticate through public Google SSO
            if (user.getRole() == Role.ADMIN) {
                log.warn("[DIAGNOSTIC] correlationId={} stage=ROLE_ASSIGNED success=false reason=ADMIN_BLOCKED", correlationId);
                throw new AccessDeniedException("Administrative accounts cannot authenticate via standard Google SSO.");
            }
            if (!user.isActive() || user.isDeleted()) {
                throw new BadCredentialsException("Account is deactivated or deleted.");
            }

            boolean needsUpdate = false;
            if (picture != null && !picture.equals(user.getProfilePicture())) {
                user.setProfilePicture(picture);
                needsUpdate = true;
            }
            if (needsUpdate) {
                userRepository.save(user);
            }
            log.info("[DIAGNOSTIC] correlationId={} stage=VELO_USER_FOUND_OR_CREATED mode=EXISTING_GOOGLE_USER", correlationId);
        } else {
            // 2. Not found by googleId. Check if user exists by verified email (account linking flow)
            Optional<User> existingEmailUserOpt = userRepository.findByEmailIgnoreCase(verifiedEmail);

            if (existingEmailUserOpt.isPresent()) {
                user = existingEmailUserOpt.get();

                if (user.getRole() == Role.ADMIN) {
                    log.warn("[DIAGNOSTIC] correlationId={} stage=ROLE_ASSIGNED success=false reason=ADMIN_BLOCKED", correlationId);
                    throw new AccessDeniedException("Administrative accounts cannot authenticate via standard Google SSO.");
                }
                if (!user.isActive() || user.isDeleted()) {
                    throw new BadCredentialsException("Account is deactivated or deleted.");
                }

                // If account is already linked to another Google identity, reject
                if (user.getGoogleId() != null && !user.getGoogleId().equals(googleId)) {
                    throw new BadCredentialsException("This account is already linked to a different Google account.");
                }

                // Secure account linking: verify password or active session to prevent malicious identity attachment
                org.springframework.security.core.Authentication currentAuth =
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                boolean isSessionAuth = currentAuth != null
                        && currentAuth.isAuthenticated()
                        && !currentAuth.getPrincipal().equals("anonymousUser")
                        && verifiedEmail.equalsIgnoreCase(currentAuth.getName());

                if (!isSessionAuth && user.getPassword() != null && !user.getPassword().isBlank()) {
                    if (request.getPassword() != null && !request.getPassword().isBlank()) {
                        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                            throw new BadCredentialsException("Invalid password provided for account linking.");
                        }
                    } else {
                        throw new BadCredentialsException("An existing password-based account was found for " + verifiedEmail
                                + ". Please enter your password to link your Google account.");
                    }
                }

                // Safe linking
                user.setGoogleId(googleId);
                user.setAuthProvider("GOOGLE");
                if (picture != null && user.getProfilePicture() == null) {
                    user.setProfilePicture(picture);
                }
                userRepository.save(user);
                log.info("[DIAGNOSTIC] correlationId={} stage=VELO_USER_FOUND_OR_CREATED mode=LINKED_ACCOUNT", correlationId);
            } else {
                // 3. New User: Auto-register user with verified Google identity
                // Google public SSO strictly grants standard Role.PATIENT (never DOCTOR or ADMIN)
                Role targetRole = Role.PATIENT;

                user = User.builder()
                        .email(verifiedEmail)
                        .password(null) // Phase 8: Google-only users have null password
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
                user = savedUser;

                // Cascade creation of role profile
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

                notificationService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFirstName() + " " + savedUser.getLastName());
                log.info("[DIAGNOSTIC] correlationId={} stage=VELO_USER_FOUND_OR_CREATED mode=NEW_GOOGLE_USER", correlationId);
            }
        }

        log.info("[DIAGNOSTIC] correlationId={} stage=ROLE_ASSIGNED role={}", correlationId, user.getRole());

        String jwt = jwtUtils.generateToken(user.getEmail(), user.getRole().name());
        log.info("[DIAGNOSTIC] correlationId={} stage=JWT_CREATED success=true", correlationId);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[DIAGNOSTIC] correlationId={} stage=AUTH_RESPONSE_SENT duration={}ms", correlationId, duration);

        return AuthResponse.builder()
                .token(jwt)
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }
}

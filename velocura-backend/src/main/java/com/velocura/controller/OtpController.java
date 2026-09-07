package com.velocura.controller;

import com.velocura.dto.OtpDetailResponse;
import com.velocura.model.OtpVerification;
import com.velocura.model.User;
import com.velocura.repository.OtpVerificationRepository;
import com.velocura.repository.UserRepository;
import com.velocura.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

    private static final Logger log = LoggerFactory.getLogger(OtpController.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long OTP_VALIDITY_MINUTES = 5;
    private static final long RESEND_COOLDOWN_MS = 30_000;
    private static final int MAX_ATTEMPTS = 5;

    private static final Map<String, Long> lastSentMap = new ConcurrentHashMap<>();

    private static OtpVerificationRepository staticOtpRepository;

    private final OtpVerificationRepository otpRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Autowired
    public OtpController(
            OtpVerificationRepository otpRepository,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.otpRepository = otpRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        staticOtpRepository = otpRepository;
    }

    public static String hashOtp(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String getActiveOtp(String email) {
        if (email == null || staticOtpRepository == null) return null;
        String cleaned = email.toLowerCase().trim();
        Optional<OtpVerification> opt = staticOtpRepository.findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(cleaned);
        if (opt.isPresent() && !opt.get().isExpired() && !opt.get().isMaxAttemptsReached()) {
            return "******"; // Masked for security - never expose plaintext active OTP
        }
        return null;
    }

    public static List<OtpDetailResponse> getActiveOtpsList(UserRepository userRepository) {
        List<OtpDetailResponse> list = new ArrayList<>();
        if (staticOtpRepository == null) return list;

        List<OtpVerification> all = staticOtpRepository.findAll();
        for (OtpVerification v : all) {
            if (!v.isConsumed() && !v.isExpired() && !v.isMaxAttemptsReached()) {
                String email = v.getEmail();
                Optional<User> userOpt = userRepository != null ? userRepository.findByEmailIgnoreCase(email) : Optional.empty();
                boolean registered = userOpt.isPresent();
                String userName = registered ? (userOpt.get().getFirstName() + " " + userOpt.get().getLastName()) : "Registration Pending";
                String role = registered ? userOpt.get().getRole().name() : "GUEST";

                list.add(OtpDetailResponse.builder()
                        .email(email)
                        .code("******") // Masked - never leak active code via API
                        .isRegisteredUser(registered)
                        .userName(userName)
                        .role(role)
                        .expiryTime(v.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                        .build());
            }
        }
        return list;
    }

    public static void generateAndSendOtp(String email, NotificationService notificationService) {
        if (email == null || email.isBlank()) return;
        String cleaned = email.toLowerCase().trim();

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String codeHash = hashOtp(code);
        LocalDateTime now = LocalDateTime.now();

        if (staticOtpRepository != null) {
            OtpVerification record = OtpVerification.builder()
                    .email(cleaned)
                    .codeHash(codeHash)
                    .attemptsCount(0)
                    .maxAttempts(MAX_ATTEMPTS)
                    .createdAt(now)
                    .expiresAt(now.plusMinutes(OTP_VALIDITY_MINUTES))
                    .isConsumed(false)
                    .build();
            staticOtpRepository.save(record);
        }
        lastSentMap.put(cleaned, System.currentTimeMillis());

        log.info("Secure OTP issued and dispatched for: {}", cleaned);
        if (notificationService != null) {
            notificationService.sendOtpEmail(cleaned, code);
        }
    }

    public static boolean verifyAndRemoveOtp(String email, String code) {
        if (email == null || code == null || staticOtpRepository == null) return false;
        String cleaned = email.toLowerCase().trim();
        Optional<OtpVerification> opt = staticOtpRepository.findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(cleaned);
        if (opt.isEmpty()) return false;

        OtpVerification record = opt.get();
        if (record.isExpired() || record.isMaxAttemptsReached()) {
            return false;
        }

        String inputHash = hashOtp(code.trim());
        if (MessageDigest.isEqual(record.getCodeHash().getBytes(StandardCharsets.UTF_8), inputHash.getBytes(StandardCharsets.UTF_8))) {
            record.setConsumed(true);
            staticOtpRepository.save(record);
            lastSentMap.remove(cleaned);
            return true;
        } else {
            record.setAttemptsCount(record.getAttemptsCount() + 1);
            staticOtpRepository.save(record);
            return false;
        }
    }

    public static String issueOtpForAdmin(String email, NotificationService notificationService) {
        generateAndSendOtp(email, notificationService);
        return "Dispatched to " + email;
    }

    public static boolean revokeOtp(String email) {
        if (email == null || staticOtpRepository == null) return false;
        String cleaned = email.toLowerCase().trim();
        lastSentMap.remove(cleaned);
        Optional<OtpVerification> opt = staticOtpRepository.findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(cleaned);
        if (opt.isPresent()) {
            OtpVerification record = opt.get();
            record.setConsumed(true);
            staticOtpRepository.save(record);
            return true;
        }
        return false;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: Email is required."));
        }

        String cleanedEmail = email.toLowerCase().trim();

        // 1. Resend Rate Limiting: 30-second cooldown
        Long lastSent = lastSentMap.get(cleanedEmail);
        if (lastSent != null && (System.currentTimeMillis() - lastSent < RESEND_COOLDOWN_MS)) {
            long remainingSeconds = (RESEND_COOLDOWN_MS - (System.currentTimeMillis() - lastSent)) / 1000;
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    "Please wait " + Math.max(1, remainingSeconds) + " seconds before requesting a new security code."
            );
        }

        // 2. Invalidate any existing active OTP for this email
        Optional<OtpVerification> existing = otpRepository.findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(cleanedEmail);
        existing.ifPresent(v -> {
            v.setConsumed(true);
            otpRepository.save(v);
        });

        // 3. Generate Cryptographically Secure 6-digit code
        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String codeHash = hashOtp(otpCode);
        LocalDateTime now = LocalDateTime.now();

        OtpVerification record = OtpVerification.builder()
                .email(cleanedEmail)
                .codeHash(codeHash)
                .attemptsCount(0)
                .maxAttempts(MAX_ATTEMPTS)
                .createdAt(now)
                .expiresAt(now.plusMinutes(OTP_VALIDITY_MINUTES))
                .isConsumed(false)
                .build();
        otpRepository.save(record);
        lastSentMap.put(cleanedEmail, System.currentTimeMillis());

        log.info("Secure OTP issued and dispatched for: {}", cleanedEmail);
        notificationService.sendOtpEmail(cleanedEmail, otpCode);

        // NEVER return the active code in API response
        return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "Verification code sent successfully to " + cleanedEmail
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");

        if (email == null || code == null || email.trim().isEmpty() || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Email and code are required."));
        }

        String cleanedEmail = email.toLowerCase().trim();
        Optional<OtpVerification> opt = otpRepository.findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(cleanedEmail);

        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No active verification session found for this email."));
        }

        OtpVerification record = opt.get();

        // 1. Expiration check
        if (record.isExpired()) {
            record.setConsumed(true);
            otpRepository.save(record);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Verification code has expired. Please request a new one."));
        }

        // 2. Max attempts check
        if (record.isMaxAttemptsReached()) {
            record.setConsumed(true);
            otpRepository.save(record);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "success", false,
                    "message", "Maximum verification attempts exceeded. Please request a new code."
            ));
        }

        // 3. Constant-time Hash Comparison
        String inputHash = hashOtp(code.trim());
        boolean matches = MessageDigest.isEqual(
                record.getCodeHash().getBytes(StandardCharsets.UTF_8),
                inputHash.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            record.setAttemptsCount(record.getAttemptsCount() + 1);
            otpRepository.save(record);
            int remaining = MAX_ATTEMPTS - record.getAttemptsCount();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid verification code. Remaining attempts: " + Math.max(0, remaining)
            ));
        }

        // 4. One-time consumption: immediately mark consumed
        record.setConsumed(true);
        otpRepository.save(record);
        lastSentMap.remove(cleanedEmail);

        log.info("OTP successfully verified and consumed for: {}", cleanedEmail);
        return ResponseEntity.ok().body(Map.of("success", true, "message", "OTP verified successfully!"));
    }
}

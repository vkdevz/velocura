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
    private static final Map<String, EphemeralOtp> ephemeralOtpCache = new ConcurrentHashMap<>();

    private static OtpVerificationRepository staticOtpRepository;

    private final OtpVerificationRepository otpRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public static class EphemeralOtp {
        private final String code;
        private final long expiryTimestamp;

        public EphemeralOtp(String code, long expiryTimestamp) {
            this.code = code;
            this.expiryTimestamp = expiryTimestamp;
        }

        public String getCode() {
            return code;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTimestamp;
        }
    }

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

    public static String getActiveOtpPlaintextForAdmin(String email) {
        if (email == null || email.isBlank() || staticOtpRepository == null) return null;
        String cleaned = email.toLowerCase().trim();
        Optional<OtpVerification> opt = staticOtpRepository.findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(cleaned);
        if (opt.isEmpty() || opt.get().isExpired() || opt.get().isMaxAttemptsReached() || opt.get().isConsumed()) {
            ephemeralOtpCache.remove(cleaned);
            return null;
        }
        EphemeralOtp cached = ephemeralOtpCache.get(cleaned);
        if (cached != null && !cached.isExpired()) {
            return cached.getCode();
        }
        return null;
    }

    public static List<OtpDetailResponse> getActiveOtpsList(UserRepository userRepository) {
        List<OtpDetailResponse> list = new ArrayList<>();
        if (staticOtpRepository == null) return list;

        List<OtpVerification> all = staticOtpRepository.findAll();
        // Deduplicate by email, keeping the latest active record
        Map<String, OtpVerification> latestActiveMap = new LinkedHashMap<>();
        for (OtpVerification v : all) {
            if (!v.isConsumed() && !v.isExpired() && !v.isMaxAttemptsReached()) {
                String email = v.getEmail().toLowerCase().trim();
                OtpVerification existing = latestActiveMap.get(email);
                if (existing == null || v.getCreatedAt().isAfter(existing.getCreatedAt())) {
                    latestActiveMap.put(email, v);
                }
            }
        }

        for (OtpVerification v : latestActiveMap.values()) {
            String email = v.getEmail();
            Optional<User> userOpt = userRepository != null ? userRepository.findByEmailIgnoreCase(email) : Optional.empty();
            boolean registered = userOpt.isPresent();
            String userName = registered ? (userOpt.get().getFirstName() + " " + userOpt.get().getLastName()) : "Registration Pending";
            String role = registered ? userOpt.get().getRole().name() : "GUEST";

            list.add(OtpDetailResponse.builder()
                    .email(email)
                    .code("******") // Masked - never leak active code via general API
                    .isRegisteredUser(registered)
                    .userName(userName)
                    .role(role)
                    .expiryTime(v.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .build());
        }
        return list;
    }

    public static void generateAndSendOtp(String email, NotificationService notificationService) {
        if (email == null || email.isBlank()) return;
        String cleaned = email.toLowerCase().trim();

        // Invalidate all prior unconsumed OTP records for this email
        if (staticOtpRepository != null) {
            List<OtpVerification> previousList = staticOtpRepository.findByEmailIgnoreCaseAndIsConsumedFalse(cleaned);
            for (OtpVerification prev : previousList) {
                prev.setConsumed(true);
                staticOtpRepository.save(prev);
            }
        }

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
        ephemeralOtpCache.put(cleaned, new EphemeralOtp(code, System.currentTimeMillis() + (OTP_VALIDITY_MINUTES * 60 * 1000)));

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
            ephemeralOtpCache.remove(cleaned);
            return false;
        }

        String inputHash = hashOtp(code.trim());
        if (MessageDigest.isEqual(record.getCodeHash().getBytes(StandardCharsets.UTF_8), inputHash.getBytes(StandardCharsets.UTF_8))) {
            record.setConsumed(true);
            staticOtpRepository.save(record);
            lastSentMap.remove(cleaned);
            ephemeralOtpCache.remove(cleaned);
            return true;
        } else {
            record.setAttemptsCount(record.getAttemptsCount() + 1);
            staticOtpRepository.save(record);
            if (record.isMaxAttemptsReached()) {
                ephemeralOtpCache.remove(cleaned);
            }
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
        ephemeralOtpCache.remove(cleaned);

        List<OtpVerification> unconsumed = staticOtpRepository.findByEmailIgnoreCaseAndIsConsumedFalse(cleaned);
        boolean revokedAny = false;
        for (OtpVerification record : unconsumed) {
            record.setConsumed(true);
            staticOtpRepository.save(record);
            revokedAny = true;
        }
        return revokedAny;
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

        // 2. Invalidate any existing active OTPs for this email
        List<OtpVerification> existingList = otpRepository.findByEmailIgnoreCaseAndIsConsumedFalse(cleanedEmail);
        for (OtpVerification ex : existingList) {
            ex.setConsumed(true);
            otpRepository.save(ex);
        }

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
        ephemeralOtpCache.put(cleanedEmail, new EphemeralOtp(otpCode, System.currentTimeMillis() + (OTP_VALIDITY_MINUTES * 60 * 1000)));

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
            ephemeralOtpCache.remove(cleanedEmail);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Verification code has expired. Please request a new one."));
        }

        // 2. Max attempts check
        if (record.isMaxAttemptsReached()) {
            record.setConsumed(true);
            otpRepository.save(record);
            ephemeralOtpCache.remove(cleanedEmail);
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
            if (record.isMaxAttemptsReached()) {
                ephemeralOtpCache.remove(cleanedEmail);
            }
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
        ephemeralOtpCache.remove(cleanedEmail);

        log.info("OTP successfully verified and consumed for: {}", cleanedEmail);
        return ResponseEntity.ok().body(Map.of("success", true, "message", "OTP verified successfully!"));
    }
}

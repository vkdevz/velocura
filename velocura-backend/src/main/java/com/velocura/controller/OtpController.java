package com.velocura.controller;

import com.velocura.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

    @Autowired
    private NotificationService notificationService;

    // Simple thread-safe in-memory cache for OTP tracking (Email -> OtpEntry)
    private static final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static class OtpEntry {
        String code;
        long expiryTime;

        OtpEntry(String code, long expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private static final Map<String, Long> lastSentMap = new ConcurrentHashMap<>();

    public static String getActiveOtp(String email) {
        if (email == null) return null;
        String cleanedEmail = email.toLowerCase().trim();
        OtpEntry entry = otpCache.get(cleanedEmail);
        if (entry != null && !entry.isExpired()) {
            return entry.code;
        }
        return null;
    }

    public static java.util.List<com.velocura.dto.OtpDetailResponse> getActiveOtpsList(com.velocura.repository.UserRepository userRepository) {
        java.util.List<com.velocura.dto.OtpDetailResponse> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, OtpEntry> entry : otpCache.entrySet()) {
            if (!entry.getValue().isExpired()) {
                String email = entry.getKey();
                String code = entry.getValue().code;
                long expiry = entry.getValue().expiryTime;

                java.util.Optional<com.velocura.model.User> userOpt = userRepository.findByEmailIgnoreCase(email);
                boolean registered = userOpt.isPresent();
                String userName = registered ? (userOpt.get().getFirstName() + " " + userOpt.get().getLastName()) : "Registration Pending";
                String role = registered ? userOpt.get().getRole().name() : "GUEST";

                list.add(com.velocura.dto.OtpDetailResponse.builder()
                        .email(email)
                        .code(code)
                        .isRegisteredUser(registered)
                        .userName(userName)
                        .role(role)
                        .expiryTime(expiry)
                        .build());
            }
        }
        return list;
    }

    public static void generateAndSendOtp(String email, NotificationService notificationService) {
        Random rand = new Random();
        String cleanedEmail = email.toLowerCase().trim();
        String otpCode = String.format("%06d", rand.nextInt(1000000));
        long expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        otpCache.put(cleanedEmail, new OtpEntry(otpCode, expiry));
        lastSentMap.put(cleanedEmail, System.currentTimeMillis());

        System.out.println("\n--------------------------------------------------");
        System.out.println("📩 VELOCURA OTP NOTIFICATION SENT TO: " + cleanedEmail);
        System.out.println("🔑 CODE: " + otpCode + " (Expires in 5 minutes)");
        System.out.println("--------------------------------------------------\n");

        notificationService.sendOtpEmail(cleanedEmail, otpCode);
    }

    public static boolean verifyAndRemoveOtp(String email, String code) {
        if (email == null || code == null) return false;
        String cleanedEmail = email.toLowerCase().trim();
        OtpEntry entry = otpCache.get(cleanedEmail);
        if (entry == null || entry.isExpired() || !entry.code.equals(code.trim())) {
            return false;
        }
        otpCache.remove(cleanedEmail);
        lastSentMap.remove(cleanedEmail);
        return true;
    }

    public static String issueOtpForAdmin(String email, NotificationService notificationService) {
        if (email == null || email.trim().isEmpty()) return null;
        String cleanedEmail = email.toLowerCase().trim();
        Random rand = new Random();
        String otpCode = String.format("%06d", rand.nextInt(1000000));
        long expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        otpCache.put(cleanedEmail, new OtpEntry(otpCode, expiry));
        lastSentMap.put(cleanedEmail, System.currentTimeMillis());

        System.out.println("\n--------------------------------------------------");
        System.out.println("📩 VELOCURA ADMIN OTP DISPATCH TO: " + cleanedEmail);
        System.out.println("🔑 CODE: " + otpCode + " (Expires in 5 minutes)");
        System.out.println("--------------------------------------------------\n");

        if (notificationService != null) {
            notificationService.sendOtpEmail(cleanedEmail, otpCode);
        }
        return otpCode;
    }

    public static boolean revokeOtp(String email) {
        if (email == null) return false;
        String cleanedEmail = email.toLowerCase().trim();
        lastSentMap.remove(cleanedEmail);
        return otpCache.remove(cleanedEmail) != null;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Email is required.");
        }

        String cleanedEmail = email.toLowerCase().trim();

        // Resend Rate Limiting Algorithm (30-second cooldown per email)
        Long lastSent = lastSentMap.get(cleanedEmail);
        if (lastSent != null && (System.currentTimeMillis() - lastSent < 30_000)) {
            long remainingSeconds = (30_000 - (System.currentTimeMillis() - lastSent)) / 1000;
            return ResponseEntity.status(429).body("Please wait " + remainingSeconds + " seconds before requesting a new security code.");
        }

        // Generate clean 6-digit random code
        String otpCode = String.format("%06d", random.nextInt(1000000));
        long expiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        otpCache.put(cleanedEmail, new OtpEntry(otpCode, expiry));
        lastSentMap.put(cleanedEmail, System.currentTimeMillis());

        System.out.println("\n--------------------------------------------------");
        System.out.println("📩 VELOCURA OTP NOTIFICATION SENT TO: " + cleanedEmail);
        System.out.println("🔑 CODE: " + otpCode + " (Expires in 5 minutes)");
        System.out.println("--------------------------------------------------\n");

        notificationService.sendOtpEmail(cleanedEmail, otpCode);

        return ResponseEntity.ok().body(Map.of(
            "success", true,
            "message", "Verification code sent successfully to " + cleanedEmail,
            "code", otpCode
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
        OtpEntry entry = otpCache.get(cleanedEmail);

        if (entry == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No verification session found for this email."));
        }

        if (entry.isExpired()) {
            otpCache.remove(cleanedEmail);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Verification code has expired. Please request a new one."));
        }

        if (!entry.code.equals(code.trim())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid verification code. Please check and try again."));
        }

        // Clean cache on successful match
        otpCache.remove(cleanedEmail);
        return ResponseEntity.ok().body(Map.of("success", true, "message", "OTP verified successfully!"));
    }
}

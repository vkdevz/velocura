package com.velocura.controller;

import com.velocura.dto.AuthResponse;
import com.velocura.dto.LoginRequest;
import com.velocura.dto.RegisterRequest;
import com.velocura.model.Doctor;
import com.velocura.model.Patient;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.DoctorRepository;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import com.velocura.security.JwtUtils;
import com.velocura.security.TokenBlacklistService;
import com.velocura.dto.GoogleAuthRequest;
import com.velocura.service.GoogleAuthService;
import com.velocura.service.NotificationService;
import com.velocura.service.GeminiAiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final NotificationService notificationService;
    private final GeminiAiService geminiAiService;

    private final TokenBlacklistService tokenBlacklistService;
    private final com.velocura.service.AuditService auditService;

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private com.velocura.service.AdminService adminService;

    @Autowired
    public AuthController(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtils jwtUtils,
            NotificationService notificationService,
            GeminiAiService geminiAiService,
            TokenBlacklistService tokenBlacklistService,
            com.velocura.service.AuditService auditService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.notificationService = notificationService;
        this.geminiAiService = geminiAiService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        // Anti-Spam: Block Admin role self-registration
        if (registerRequest.getRole() == Role.ADMIN) {
            return ResponseEntity.badRequest().body("Error: Administrative accounts cannot be created via public registration.");
        }

        // Anti-Spam: Block disposable / temporary email domains
        String email = registerRequest.getEmail() != null ? registerRequest.getEmail().toLowerCase().trim() : "";
        List<String> spamDomains = List.of(
            "mailinator.com", "tempmail.com", "dispostable.com", "10minutemail.com", 
            "guerrillamail.com", "trashmail.com", "yopmail.com", "sharklasers.com", 
            "getnada.com", "throwawaymail.com", "temp-mail.org", "fakeinbox.com"
        );
        boolean isSpamDomain = spamDomains.stream().anyMatch(email::endsWith);
        if (isSpamDomain) {
            return ResponseEntity.badRequest().body("Error: Disposable or temporary email providers are not permitted. Please use a valid email address.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        // 1. Create and save base User
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .role(registerRequest.getRole())
                .isActive(true)
                .build();

        userRepository.save(user);

        // 2. Cascade saving specific profile details based on User Role
        if (registerRequest.getRole() == Role.PATIENT) {
            Patient patient = Patient.builder()
                    .user(user)
                    .dateOfBirth(registerRequest.getDateOfBirth())
                    .gender(registerRequest.getGender())
                    .phoneNumber(registerRequest.getPhoneNumber())
                    .bloodGroup(registerRequest.getBloodGroup())
                    .address(registerRequest.getAddress())
                    .build();
            patientRepository.save(patient);
        } else if (registerRequest.getRole() == Role.DOCTOR) {
            Doctor doctor = Doctor.builder()
                    .user(user)
                    .specialization(registerRequest.getSpecialization())
                    .licenseNumber(registerRequest.getLicenseNumber())
                    .experienceYears(registerRequest.getExperienceYears())
                    .consultationFee(registerRequest.getConsultationFee())
                    .biography(registerRequest.getBiography())
                    .isVerified(false) // Admin approval verification flow
                    .build();
            doctorRepository.save(doctor);
        }

        // Send Welcome email
        notificationService.sendWelcomeEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName());

        // Generate JWT token for seamless auto-login
        String jwt = jwtUtils.generateToken(user.getEmail(), user.getRole().name());

        auditService.logEvent(user.getId(), user.getEmail(), user.getRole().name(), "REGISTER", "User", String.valueOf(user.getId()), "CLIENT", "SUCCESS", "New user registered");

        return ResponseEntity.ok(AuthResponse.builder()
                .token(jwt)
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (org.springframework.security.core.AuthenticationException e) {
            auditService.logEvent(null, loginRequest.getEmail(), "ANONYMOUS", "LOGIN_FAILED", "User", null, "CLIENT", "DENIED", "Invalid credentials");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body("Error: Invalid email or password");
        }

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: Authenticated user not found in database."));

        String jwt = jwtUtils.generateToken(user.getEmail(), user.getRole().name());

        auditService.logEvent(user.getId(), user.getEmail(), user.getRole().name(), "LOGIN_SUCCESS", "User", String.valueOf(user.getId()), "CLIENT", "SUCCESS", "User logged in");

        return ResponseEntity.ok(AuthResponse.builder()
                .token(jwt)
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                java.util.Date expiry = jwtUtils.getExpirationFromToken(jwt);
                tokenBlacklistService.blacklistToken(jwt, expiry != null ? expiry.getTime() : System.currentTimeMillis() + 86400000);
            } catch (Exception e) {
                // If token malformed or already expired, still blacklist string
                tokenBlacklistService.blacklistToken(jwt, System.currentTimeMillis() + 3600000);
            }
        }
        SecurityContextHolder.clearContext();
        auditService.logSuccess("LOGOUT", "User", null, "User logged out and token invalidated");
        return ResponseEntity.ok(java.util.Map.of("message", "User logged out successfully and session terminated."));
    }

    @PostMapping("/google")
    public ResponseEntity<?> authenticateWithGoogle(@RequestBody GoogleAuthRequest googleAuthRequest) {
        try {
            AuthResponse response = googleAuthService.authenticateWithGoogle(googleAuthRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Google Auth Exception: " + e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: Unable to complete Google authentication. " + e.getMessage());
        }
    }

    @GetMapping("/version")
    public ResponseEntity<java.util.Map<String, String>> getSystemVersion() {
        return ResponseEntity.ok(java.util.Map.of(
            "routerVersion", "conversational-gatekeeper-v2",
            "service", "velocura-backend",
            "status", "UP"
        ));
    }

    @PostMapping("/triage")
    public ResponseEntity<com.velocura.dto.TriageResponse> anonymousTriage(@RequestBody com.velocura.dto.TriageRequest request) {
        String symptoms = request.getSymptoms() != null ? request.getSymptoms() : "";
        
        // Attempt advanced AI chat triage first
        com.velocura.dto.TriageResponse aiResponse = geminiAiService.callGeminiApi(symptoms, request.getHistory());
        if (aiResponse != null) {
            return ResponseEntity.ok(aiResponse);
        }

        // Advanced Clinical AI Triage Engine
        String query = symptoms.toLowerCase();
        String triageLevel = "Mild";
        String clinicalSummary = "Based on your symptom inputs, initial screening indicates low to moderate risk. We recommend home care, rest, and routine clinical evaluation if symptoms persist beyond 48 hours.";
        String recommendedSpecialty = "General Medicine";
        java.util.List<String> differentialDiagnoses = java.util.List.of("Mild Viral Syndrome", "Physical Fatigue / Tension", "Common Cold");
        java.util.List<String> immediatePrecautions = java.util.List.of("Maintain adequate hydration", "Monitor body temperature every 6 hours", "Avoid heavy physical exertion");
        java.util.List<String> homeRemedies = java.util.List.of("Warm water fluids & herbal tea", "Ensure 7-8 hours of uninterrupted sleep", "Light nutrient-dense diet");
        java.util.List<String> suggestedOtc = java.util.List.of("Paracetamol 650mg (for pain/fever - max 3g/day)", "Electrolyte hydration salts (ORS)");

        // 1. CARDIOLOGY & ACUTE CIRCULATORY STRESS
        if (query.contains("chest") || query.contains("heart") || query.contains("palpitation") || query.contains("breathless") || query.contains("cardiac") || query.contains("angina") || query.contains("arm pain")) {
            triageLevel = "Critical";
            clinicalSummary = "High-priority clinical alert: Symptoms strongly indicate potential cardiovascular strain, ischemic distress, or acute respiratory compromise. Immediate clinical evaluation is required.";
            recommendedSpecialty = "Cardiology";
            differentialDiagnoses = java.util.List.of("Angina Pectoris", "Myocardial Infarction / Ischemia", "Arrhythmia", "Pericarditis");
            immediatePrecautions = java.util.List.of("Cease all physical activity immediately", "Sit in an upright comfortable position", "Call emergency services if chest pressure radiates to arm or jaw");
            homeRemedies = java.util.List.of("Stay calm in a well-ventilated quiet area", "Practice slow, controlled diaphragmatic breathing");
            suggestedOtc = java.util.List.of("Aspirin 325mg (chewable - consult emergency dispatcher before consuming)");

        // 2. NEUROLOGY & CEREBROVASCULAR
        } else if (query.contains("headache") || query.contains("migraine") || query.contains("dizzy") || query.contains("faint") || query.contains("seizure") || query.contains("numbness") || query.contains("paralysis") || query.contains("speech")) {
            boolean isCriticalStroke = query.contains("paralysis") || query.contains("speech") || query.contains("seizure") || query.contains("faint");
            triageLevel = isCriticalStroke ? "Critical" : "Moderate";
            clinicalSummary = isCriticalStroke 
                ? "Critical neurological alert: Reported symptoms indicate acute central nervous system distress or focal neurological deficit."
                : "Described headache, sensory aura, or vertigo symptoms indicate migraine cascade or tension-type cranial vascular contraction.";
            recommendedSpecialty = "Neurology";
            differentialDiagnoses = isCriticalStroke 
                ? java.util.List.of("Transient Ischemic Attack (TIA)", "Acute Seizure Episode", "Syncope")
                : java.util.List.of("Migraine Episode with Aura", "Tension Headache", "Vestibular Vertigo / Neuritis");
            immediatePrecautions = java.util.List.of("Rest immediately in a dark, quiet room", "Avoid blue light screens and loud noises", "Avoid sudden neck or head movements");
            homeRemedies = java.util.List.of("Cold compress across forehead or nape of neck", "Peppermint oil temple massage", "Hydrate with electrolyte fluids");
            suggestedOtc = java.util.List.of("Ibuprofen 400mg (take with food)", "Acetaminophen 500mg (for pain management)");

        // 3. PULMONOLOGY & RESPIRATORY
        } else if (query.contains("cough") || query.contains("wheez") || query.contains("asthma") || query.contains("phlegm") || query.contains("bronchitis") || query.contains("pneumonia") || query.contains("lung")) {
            boolean isSevereResp = query.contains("wheez") || query.contains("asthma") || query.contains("pneumonia");
            triageLevel = isSevereResp ? "Moderate" : "Mild";
            clinicalSummary = "Respiratory evaluation indicates upper/lower airway mucosal inflammation, broncho-constriction, or bronchial infection.";
            recommendedSpecialty = "Pulmonology";
            differentialDiagnoses = java.util.List.of("Acute Bronchitis", "Asthma Exacerbation", "Viral Upper Respiratory Tract Infection");
            immediatePrecautions = java.util.List.of("Avoid cold air exposures and dusty environments", "Stay away from active/passive tobacco smoke", "Use a pulse oximeter to track SpO2 levels");
            homeRemedies = java.util.List.of("Steam inhalation with eucalyptus oil (2-3 times daily)", "Warm honey and ginger liquid rinses", "Elevate head with pillows while sleeping");
            suggestedOtc = java.util.List.of("Dextromethorphan syrup (for dry cough)", "Guaifenesin 400mg (expectorant for phlegm)");

        // 4. GASTROENTEROLOGY & DIGESTIVE SYSTEM
        } else if (query.contains("stomach") || query.contains("acid") || query.contains("vomit") || query.contains("diarrhea") || query.contains("nausea") || query.contains("ulcer") || query.contains("bloat") || query.contains("gerd") || query.contains("abdominal")) {
            triageLevel = query.contains("vomit") || query.contains("diarrhea") ? "Moderate" : "Mild";
            clinicalSummary = "Gastrointestinal screening indicates gastric mucosal hyperacidity, enteric viral irritation, or smooth muscle spasm.";
            recommendedSpecialty = "Gastroenterology";
            differentialDiagnoses = java.util.List.of("Acute Gastroenteritis", "GERD / Acid Reflux", "Functional Dyspepsia", "Irritable Bowel Syndrome (IBS)");
            immediatePrecautions = java.util.List.of("Avoid spicy, greasy, acidic, or fried foods", "Do not lie down immediately after eating (wait 2 hours)", "Sip fluids slowly to prevent vomiting");
            homeRemedies = java.util.List.of("BRAT diet (Bananas, Rice, Applesauce, Toast)", "Chamomile or peppermint tea", "Warm hot-water bag on abdomen for cramp relief");
            suggestedOtc = java.util.List.of("Pantoprazole 40mg / Omeprazole 20mg (for acidity)", "Oral Rehydration Salts (ORS) for fluid loss", "Dicyclomine / Antacids for cramps");

        // 5. ORTHOPEDICS & MUSCULOSKELETAL
        } else if (query.contains("fracture") || query.contains("bone") || query.contains("joint") || query.contains("sprain") || query.contains("back pain") || query.contains("arthritis") || query.contains("swelling") || query.contains("ligament")) {
            triageLevel = query.contains("fracture") ? "Critical" : "Moderate";
            clinicalSummary = "Musculoskeletal assessment suggests acute ligamentous sprain, articular joint inflammation, or mechanical strain.";
            recommendedSpecialty = "Orthopedics";
            differentialDiagnoses = java.util.List.of("Ligament Sprain / Tendonitis", "Lumbar Strain", "Osteoarthritis Flare-up", "Hairline Fracture");
            immediatePrecautions = java.util.List.of("Avoid bearing weight on the affected limb/joint", "Immobilize the joint using a crepe bandage or splint", "Do not massage inflamed acute injuries");
            homeRemedies = java.util.List.of("R.I.C.E. Protocol: Rest, Ice (15 mins), Compression, Elevation", "Warm Epsom salt baths (for chronic muscle aches)");
            suggestedOtc = java.util.List.of("Ibuprofen 400mg (anti-inflammatory pain relief)", "Diclofenac topical gel application");

        // 6. DERMATOLOGY & CUTANEOUS HYPERSENSITIVITY
        } else if (query.contains("skin") || query.contains("rash") || query.contains("itch") || query.contains("hives") || query.contains("acne") || query.contains("eczema") || query.contains("allergy") || query.contains("psoriasis")) {
            triageLevel = "Mild";
            clinicalSummary = "Cutaneous analysis shows localized epidermal hypersensitivity, histaminic flare, or dermatological barrier disruption.";
            recommendedSpecialty = "Dermatology";
            differentialDiagnoses = java.util.List.of("Contact Dermatitis", "Urticaria (Hives)", "Eczema (Atopic Dermatitis)", "Viral Exanthem");
            immediatePrecautions = java.util.List.of("Do not scratch or rub the affected skin area", "Avoid perfumed soaps, synthetic fabrics, and hot water", "Keep the skin clean and hydrated");
            homeRemedies = java.util.List.of("Apply pure aloe vera gel or cold compresses", "Oatmeal bath for widespread itching");
            suggestedOtc = java.util.List.of("Cetirizine 10mg or Levocetirizine 5mg (once daily for itching)", "Calamine lotion for spot relief");

        // 7. ENT & OPHTHALMOLOGY
        } else if (query.contains("throat") || query.contains("ear") || query.contains("sinus") || query.contains("tonsil") || query.contains("eye") || query.contains("nasal") || query.contains("vision")) {
            triageLevel = "Mild";
            clinicalSummary = "Otorhinolaryngology screening reveals pharyngeal mucosal congestion, eustachian tube pressure, or allergic rhinitis.";
            recommendedSpecialty = "ENT (Otolaryngology)";
            differentialDiagnoses = java.util.List.of("Acute Pharyngitis / Tonsillitis", "Allergic Rhinitis / Sinusitis", "Otitis Media / External Ear Irritation");
            immediatePrecautions = java.util.List.of("Avoid cold beverages and ice creams", "Do not insert cotton swabs into ear canals", "Use clean sterile water for eye washes");
            homeRemedies = java.util.List.of("Warm saline gargles 3-4 times daily", "Steam inhalation", "Honey with warm turmeric water");
            suggestedOtc = java.util.List.of("Oxymetazoline / Saline Nasal Spray", "Dequalinium / Benzydamine lozenges for throat pain");

        // 8. PSYCHIATRY & BEHAVIORAL WELLNESS
        } else if (query.contains("anxiety") || query.contains("panic") || query.contains("insomnia") || query.contains("sleep") || query.contains("depression") || query.contains("stress") || query.contains("mood")) {
            triageLevel = "Moderate";
            clinicalSummary = "Psychological triage indicates neurochemical stress activation, autonomic anxiety response, or circadian sleep disruptions.";
            recommendedSpecialty = "Psychiatry & Behavioral Health";
            differentialDiagnoses = java.util.List.of("Generalized Anxiety Response", "Panic Attack Episode", "Primary Insomnia / Circadian Rhythm Disruption");
            immediatePrecautions = java.util.List.of("Engage in 5-4-3-2-1 sensory grounding techniques during high anxiety", "Limit caffeine and stimulant intake after 2 PM", "Disconnect from screen media 1 hour prior to sleep");
            homeRemedies = java.util.List.of("4-7-8 deep breathing exercises", "Chamomile tea before sleep", "Progressive muscle relaxation (PMR)");
            suggestedOtc = java.util.List.of("Melatonin 3mg-5mg (short-term sleep aid)", "L-Theanine / Herbal relaxation teas");
        }

        com.velocura.dto.TriageResponse response = com.velocura.dto.TriageResponse.builder()
                .triageLevel(triageLevel)
                .clinicalSummary(clinicalSummary)
                .recommendedSpecialty(recommendedSpecialty)
                .differentialDiagnoses(differentialDiagnoses)
                .immediatePrecautions(immediatePrecautions)
                .homeRemedies(homeRemedies)
                .suggestedOtc(suggestedOtc)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody java.util.Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Email address is required.");
        }

        String cleanedEmail = email.toLowerCase().trim();
        if (!userRepository.existsByEmail(cleanedEmail)) {
            return ResponseEntity.badRequest().body("Error: No user account found with that email address.");
        }

        // Generate and dispatch reset OTP
        OtpController.generateAndSendOtp(cleanedEmail, notificationService);

        return ResponseEntity.ok().body(java.util.Map.of(
            "message", "A password reset verification code has been dispatched to " + cleanedEmail
        ));
    }

    @PostMapping("/reset-password/verify")
    public ResponseEntity<?> verifyPasswordReset(@RequestBody java.util.Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        String newPassword = request.get("newPassword");

        if (email == null || code == null || newPassword == null || email.trim().isEmpty() || code.trim().isEmpty() || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Email, verification code, and new password are required.");
        }

        String cleanedEmail = email.toLowerCase().trim();
        boolean matched = OtpController.verifyAndRemoveOtp(cleanedEmail, code);
        if (!matched) {
            return ResponseEntity.badRequest().body("Error: Invalid or expired password reset verification code.");
        }

        User user = userRepository.findByEmail(cleanedEmail)
                .orElseThrow(() -> new RuntimeException("Error: User profile not found."));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok().body(java.util.Map.of(
            "message", "Password has been successfully updated! You can now log in with your new password."
        ));
    }

    @PostMapping("/profile/delete/request")
    public ResponseEntity<?> requestProfileDeletionOtp() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body("Error: Access denied. Must be authenticated.");
        }

        String currentEmail = auth.getName();
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Error: Authenticated user profile not found."));

        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.badRequest().body("Error: Administrative accounts cannot self-delete.");
        }

        // Generate and send OTP code specifically for account self-deletion confirmation
        OtpController.generateAndSendOtp(currentEmail, notificationService);

        return ResponseEntity.ok(java.util.Map.of(
            "message", "A secure account deletion verification code has been dispatched to your registered email."
        ));
    }

    @PostMapping("/profile/delete/confirm")
    @Transactional
    public ResponseEntity<?> confirmProfileDeletion(@RequestBody java.util.Map<String, String> requestBody) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body("Error: Access denied. Must be authenticated.");
        }

        String code = requestBody.get("code");
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Verification code is required.");
        }

        String currentEmail = auth.getName();
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Error: Authenticated user profile not found."));

        if (user.getRole() == Role.ADMIN) {
            return ResponseEntity.badRequest().body("Error: Administrative accounts cannot self-delete.");
        }

        // Verify OTP code
        boolean matched = OtpController.verifyAndRemoveOtp(currentEmail, code);
        if (!matched) {
            return ResponseEntity.badRequest().body("Error: Invalid or expired account deletion verification code.");
        }

        // Execute soft delete cascade
        adminService.deleteUser(user.getId());

        return ResponseEntity.ok(java.util.Map.of(
            "message", "Your account and associated profile records have been successfully deleted."
        ));
    }
}

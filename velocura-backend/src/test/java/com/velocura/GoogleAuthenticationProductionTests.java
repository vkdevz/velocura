package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.AuthResponse;
import com.velocura.dto.GoogleAuthRequest;
import com.velocura.dto.LoginRequest;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import com.velocura.security.oauth.GoogleTokenVerifier;
import com.velocura.security.oauth.VerifiedGoogleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoogleAuthenticationProductionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    private static final String NEW_GOOGLE_TOKEN = "prod-test-valid-new-google-token";
    private static final String DOCTOR_ATTEMPT_TOKEN = "prod-test-doctor-claim-token";
    private static final String EXPIRED_TOKEN = "prod-test-expired-token";
    private static final String UNVERIFIED_TOKEN = "prod-test-unverified-token";
    private static final String TAMPERED_SIGNATURE_TOKEN = "prod-test-tampered-signature-token";
    private static final String LINK_TARGET_TOKEN = "prod-test-link-target-token";

    @BeforeEach
    void setupMockVerifier() {
        // Valid new Google user
        VerifiedGoogleUser newVerified = VerifiedGoogleUser.builder()
                .email("prod.patient@velocura.com")
                .googleId("google-sub-prod-9901")
                .firstName("Sarah")
                .lastName("Connor")
                .picture("https://lh3.googleusercontent.com/a/sarah-connor")
                .emailVerified(true)
                .build();
        when(googleTokenVerifier.verify(eq(NEW_GOOGLE_TOKEN))).thenReturn(newVerified);

        // User attempting elevation to Doctor
        VerifiedGoogleUser docAttemptVerified = VerifiedGoogleUser.builder()
                .email("claimant@velocura.com")
                .googleId("google-sub-prod-doc-111")
                .firstName("David")
                .lastName("Tennant")
                .picture(null)
                .emailVerified(true)
                .build();
        when(googleTokenVerifier.verify(eq(DOCTOR_ATTEMPT_TOKEN))).thenReturn(docAttemptVerified);

        // User for linking
        VerifiedGoogleUser linkTargetVerified = VerifiedGoogleUser.builder()
                .email("existing.local@velocura.com")
                .googleId("google-sub-linked-777")
                .firstName("John")
                .lastName("Locke")
                .picture(null)
                .emailVerified(true)
                .build();
        when(googleTokenVerifier.verify(eq(LINK_TARGET_TOKEN))).thenReturn(linkTargetVerified);

        // Invalid / Expired / Unverified tokens
        when(googleTokenVerifier.verify(eq(EXPIRED_TOKEN)))
                .thenThrow(new BadCredentialsException("Google ID token has expired. Please sign in again."));
        when(googleTokenVerifier.verify(eq(UNVERIFIED_TOKEN)))
                .thenThrow(new BadCredentialsException("Google account email is not verified."));
        when(googleTokenVerifier.verify(eq(TAMPERED_SIGNATURE_TOKEN)))
                .thenThrow(new BadCredentialsException("Cryptographic signature verification failed."));
    }

    @Test
    @DisplayName("Scenario A: New Google user registers successfully, role is PATIENT, password is null (no fake password)")
    void testNewGoogleUser_PasswordIsNullAndRoleIsPatient() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(NEW_GOOGLE_TOKEN)
                .role(Role.PATIENT)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("prod.patient@velocura.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andReturn();

        AuthResponse authRes = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(authRes.getToken());

        // Verify DB state
        User user = userRepository.findByEmailIgnoreCase("prod.patient@velocura.com").orElseThrow();
        assertNull(user.getPassword(), "New Google-only user MUST have null password (never fake/random password)");
        assertEquals(Role.PATIENT, user.getRole(), "New Google user role must be PATIENT");
        assertEquals("GOOGLE", user.getAuthProvider());
        assertEquals("google-sub-prod-9901", user.getGoogleId());
        assertTrue(patientRepository.existsById(user.getId()), "Patient profile record must be initialized");

        // Verify that authenticated API requests work with this JWT (CustomUserDetailsService handles null password without 401)
        mockMvc.perform(get("/api/patient/profile")
                        .header("Authorization", "Bearer " + authRes.getToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scenario B: Google registration attempting DOCTOR role is strictly confined to PATIENT")
    void testGoogleRegistration_CannotElevateToDoctor() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(DOCTOR_ATTEMPT_TOKEN)
                .role(Role.DOCTOR) // Client requesting DOCTOR
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andReturn();

        User user = userRepository.findByEmailIgnoreCase("claimant@velocura.com").orElseThrow();
        assertEquals(Role.PATIENT, user.getRole(), "Public Google auth must NEVER grant DOCTOR role");
        assertNull(user.getPassword());
    }

    @Test
    @DisplayName("Scenario C: Google-only user (password=null) attempting standard password login gets clear message, no 500 error")
    void testGoogleOnlyUser_PasswordLogin_ReturnsClearMessageNotInternalError() throws Exception {
        // Register Google user
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(NEW_GOOGLE_TOKEN)
                .build();
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Now attempt password login
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("prod.patient@velocura.com");
        loginReq.setPassword("SomeRandomPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account was registered with Google. Please sign in with Google."));
    }

    @Test
    @DisplayName("Scenario D: Existing password user requires correct password to link Google identity")
    void testExistingPasswordUser_SecureAccountLinking() throws Exception {
        // 1. Create existing password user
        User local = User.builder()
                .email("existing.local@velocura.com")
                .password(passwordEncoder.encode("LocalSecret123#"))
                .firstName("John")
                .lastName("Locke")
                .role(Role.PATIENT)
                .authProvider("LOCAL")
                .isActive(true)
                .build();
        userRepository.save(local);

        // 2. Attempt Google SSO without password -> requires linking password
        GoogleAuthRequest unlinkedReq = GoogleAuthRequest.builder()
                .idToken(LINK_TARGET_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlinkedReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password")));

        // 3. Attempt Google SSO with wrong password -> rejected
        GoogleAuthRequest wrongPassReq = GoogleAuthRequest.builder()
                .idToken(LINK_TARGET_TOKEN)
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassReq)))
                .andExpect(status().isUnauthorized());

        // 4. Attempt Google SSO with correct password -> successfully links
        GoogleAuthRequest correctPassReq = GoogleAuthRequest.builder()
                .idToken(LINK_TARGET_TOKEN)
                .password("LocalSecret123#")
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPassReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // 5. Verify user is now linked
        User linkedUser = userRepository.findByEmailIgnoreCase("existing.local@velocura.com").orElseThrow();
        assertEquals("google-sub-linked-777", linkedUser.getGoogleId());
        assertEquals("GOOGLE", linkedUser.getAuthProvider());

        // 6. Subsequent Google login works without password prompt
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlinkedReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scenario E: Expired token rejected with 401 Unauthorized")
    void testExpiredToken_Rejected() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(EXPIRED_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Scenario F: Unverified email rejected with 401 Unauthorized")
    void testUnverifiedEmail_Rejected() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(UNVERIFIED_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Scenario G: Tampered signature rejected with 401 Unauthorized")
    void testTamperedSignature_Rejected() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(TAMPERED_SIGNATURE_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}

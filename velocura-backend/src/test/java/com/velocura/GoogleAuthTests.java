package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.AuthResponse;
import com.velocura.dto.GoogleAuthRequest;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import com.velocura.security.oauth.GoogleTokenVerifier;
import com.velocura.security.oauth.VerifiedGoogleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoogleAuthTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String VALID_TOKEN = "valid-google-id-token-xyz";
    private static final String EXPIRED_TOKEN = "expired-token-xyz";
    private static final String WRONG_ISSUER_TOKEN = "wrong-issuer-token";
    private static final String WRONG_AUDIENCE_TOKEN = "wrong-audience-token";
    private static final String MISSING_SUB_TOKEN = "missing-sub-token";
    private static final String UNVERIFIED_EMAIL_TOKEN = "unverified-email-token";
    private static final String LOCAL_USER_TOKEN = "token-local-user";
    private static final String CONFLICT_SUB_TOKEN = "token-conflicting-sub";
    private static final String ADMIN_TOKEN = "token-admin-user";

    @BeforeEach
    void setUp() {
        VerifiedGoogleUser verifiedUser = VerifiedGoogleUser.builder()
                .email("googleuser@velocura.com")
                .googleId("google-sub-12345")
                .firstName("Alice")
                .lastName("Smith")
                .picture("https://lh3.googleusercontent.com/a/avatar-123")
                .emailVerified(true)
                .build();

        when(googleTokenVerifier.verify(eq(VALID_TOKEN))).thenReturn(verifiedUser);
        when(googleTokenVerifier.verify(eq("invalid-token"))).thenThrow(new BadCredentialsException("Invalid token signature"));
        when(googleTokenVerifier.verify(eq(EXPIRED_TOKEN))).thenThrow(new BadCredentialsException("Token has expired."));
        when(googleTokenVerifier.verify(eq(WRONG_ISSUER_TOKEN))).thenThrow(new BadCredentialsException("Invalid token issuer."));
        when(googleTokenVerifier.verify(eq(WRONG_AUDIENCE_TOKEN))).thenThrow(new BadCredentialsException("Token audience does not match configured Client ID."));
        when(googleTokenVerifier.verify(eq(MISSING_SUB_TOKEN))).thenThrow(new BadCredentialsException("Missing subject claim in token."));
        when(googleTokenVerifier.verify(eq(UNVERIFIED_EMAIL_TOKEN))).thenThrow(new BadCredentialsException("Google email is not verified."));

        VerifiedGoogleUser localVerified = VerifiedGoogleUser.builder()
                .email("localuser@velocura.com")
                .googleId("google-sub-local-999")
                .firstName("Bob")
                .lastName("Builder")
                .picture("https://lh3.googleusercontent.com/a/avatar-bob")
                .emailVerified(true)
                .build();
        when(googleTokenVerifier.verify(eq(LOCAL_USER_TOKEN))).thenReturn(localVerified);

        VerifiedGoogleUser conflictVerified = VerifiedGoogleUser.builder()
                .email("googleuser@velocura.com")
                .googleId("google-sub-DIFFERENT-777")
                .firstName("Attacker")
                .lastName("Eve")
                .picture(null)
                .emailVerified(true)
                .build();
        when(googleTokenVerifier.verify(eq(CONFLICT_SUB_TOKEN))).thenReturn(conflictVerified);

        VerifiedGoogleUser adminVerified = VerifiedGoogleUser.builder()
                .email("admin@velocura.com")
                .googleId("google-sub-admin-000")
                .firstName("Sys")
                .lastName("Admin")
                .picture(null)
                .emailVerified(true)
                .build();
        when(googleTokenVerifier.verify(eq(ADMIN_TOKEN))).thenReturn(adminVerified);
    }

    @Test
    void testGoogleAuthRegistrationAndLogin() throws Exception {
        // 1. Google OAuth New User Registration with valid cryptographic ID token
        GoogleAuthRequest googleRegister = GoogleAuthRequest.builder()
                .idToken(VALID_TOKEN)
                .role(Role.PATIENT)
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleRegister)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse regAuth = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(regAuth.getToken());
        assertEquals("googleuser@velocura.com", regAuth.getEmail());
        assertEquals(Role.PATIENT, regAuth.getRole());
        assertEquals("Alice", regAuth.getFirstName());

        // Verify DB persistence
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase("googleuser@velocura.com");
        assertTrue(userOpt.isPresent());
        User savedUser = userOpt.get();
        assertEquals("GOOGLE", savedUser.getAuthProvider());
        assertEquals("google-sub-12345", savedUser.getGoogleId());
        assertEquals("https://lh3.googleusercontent.com/a/avatar-123", savedUser.getProfilePicture());
        assertTrue(patientRepository.existsById(savedUser.getId()));

        // 2. Google OAuth Existing User Login by Google sub
        GoogleAuthRequest googleLogin = GoogleAuthRequest.builder()
                .idToken(VALID_TOKEN)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse loginAuth = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(loginAuth.getToken());
        assertEquals("googleuser@velocura.com", loginAuth.getEmail());

        // 3. Ensure no duplicate accounts were created
        assertEquals(1, userRepository.findByGoogleId("google-sub-12345").stream().count());
    }

    @Test
    void testGoogleAuth_MissingIdToken_Rejects() throws Exception {
        GoogleAuthRequest noTokenRequest = GoogleAuthRequest.builder()
                .email("forged@example.com")
                .role(Role.PATIENT)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noTokenRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_InvalidToken_Rejects() throws Exception {
        GoogleAuthRequest invalidTokenRequest = GoogleAuthRequest.builder()
                .idToken("invalid-token")
                .role(Role.PATIENT)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTokenRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_ExpiredToken_Rejects() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(EXPIRED_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_WrongIssuer_Rejects() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(WRONG_ISSUER_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_WrongAudience_Rejects() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(WRONG_AUDIENCE_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_MissingSub_Rejects() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(MISSING_SUB_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_UnverifiedEmail_Rejects() throws Exception {
        GoogleAuthRequest req = GoogleAuthRequest.builder()
                .idToken(UNVERIFIED_EMAIL_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGoogleAuth_ClaimAdminRole_Forbidden() throws Exception {
        GoogleAuthRequest adminClaimRequest = GoogleAuthRequest.builder()
                .idToken(VALID_TOKEN)
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminClaimRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGoogleAuth_AdminAccount_CannotLoginViaGoogleSSO() throws Exception {
        // Existing admin account should not be accessible via public Google SSO
        GoogleAuthRequest adminLoginRequest = GoogleAuthRequest.builder()
                .idToken(ADMIN_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLoginRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGoogleAuth_ExistingPasswordUser_RequiresPasswordToLink() throws Exception {
        // Pre-create local password user
        User localUser = User.builder()
                .email("localuser@velocura.com")
                .password(passwordEncoder.encode("LocalSecretPassword123!"))
                .firstName("Bob")
                .lastName("Builder")
                .role(Role.PATIENT)
                .authProvider("LOCAL")
                .isActive(true)
                .build();
        userRepository.save(localUser);

        // Attempt to login with Google WITHOUT providing password -> rejected
        GoogleAuthRequest unauthenticatedLink = GoogleAuthRequest.builder()
                .idToken(LOCAL_USER_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthenticatedLink)))
                .andExpect(status().isUnauthorized());

        // Attempt with WRONG password -> rejected
        GoogleAuthRequest wrongPasswordLink = GoogleAuthRequest.builder()
                .idToken(LOCAL_USER_TOKEN)
                .password("WrongPassword123!")
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPasswordLink)))
                .andExpect(status().isUnauthorized());

        // Attempt with CORRECT password -> successfully links and logs in
        GoogleAuthRequest correctPasswordLink = GoogleAuthRequest.builder()
                .idToken(LOCAL_USER_TOKEN)
                .password("LocalSecretPassword123!")
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctPasswordLink)))
                .andExpect(status().isOk());

        // Verify user is now linked to Google sub
        User updatedUser = userRepository.findByEmailIgnoreCase("localuser@velocura.com").orElseThrow();
        assertEquals("google-sub-local-999", updatedUser.getGoogleId());
        assertEquals("GOOGLE", updatedUser.getAuthProvider());

        // Subsequent Google login works WITHOUT re-entering password (identified by Google sub)
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthenticatedLink)))
                .andExpect(status().isOk());
    }

    @Test
    void testGoogleAuth_ConflictingGoogleIdentity_Rejects() throws Exception {
        // Register user with primary Google sub
        GoogleAuthRequest initialRegister = GoogleAuthRequest.builder()
                .idToken(VALID_TOKEN)
                .role(Role.PATIENT)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRegister)))
                .andExpect(status().isOk());

        // Attempt by attacker using different Google sub for same email -> rejected
        GoogleAuthRequest conflictRequest = GoogleAuthRequest.builder()
                .idToken(CONFLICT_SUB_TOKEN)
                .build();

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRequest)))
                .andExpect(status().isUnauthorized());
    }
}


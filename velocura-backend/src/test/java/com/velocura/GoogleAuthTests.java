package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.dto.AuthResponse;
import com.velocura.dto.GoogleAuthRequest;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void testGoogleAuthRegistrationAndLogin() throws Exception {
        // 1. Google OAuth New User Registration
        GoogleAuthRequest googleRegister = GoogleAuthRequest.builder()
                .email("googleuser@velocura.com")
                .googleId("google-sub-12345")
                .firstName("Alice")
                .lastName("Smith")
                .picture("https://lh3.googleusercontent.com/a/avatar-123")
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

        // 2. Google OAuth Existing User Login
        GoogleAuthRequest googleLogin = GoogleAuthRequest.builder()
                .email("googleuser@velocura.com")
                .googleId("google-sub-12345")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse loginAuth = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(loginAuth.getToken());
        assertEquals("googleuser@velocura.com", loginAuth.getEmail());
    }
}

package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.controller.PatientController;
import com.velocura.dto.PatientProfileResponse;
import com.velocura.dto.UpdatePatientProfileRequest;
import com.velocura.security.CustomUserDetailsService;
import com.velocura.security.JwtUtils;
import com.velocura.service.PatientService;
import com.velocura.security.JwtAuthenticationFilter;
import com.velocura.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@Import(SecurityConfig.class)
class PatientControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientService patientService;

    @MockBean
    private com.velocura.service.AuditService auditService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testGetProfileSuccess() throws Exception {
        PatientProfileResponse mockProfile = PatientProfileResponse.builder()
                .email("patient@velocura.com")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1995, 5, 10))
                .gender("Male")
                .phoneNumber("1234567890")
                .bloodGroup("O+")
                .address("123 Main St")
                .build();

        Mockito.when(patientService.getPatientProfile("patient@velocura.com")).thenReturn(mockProfile);

        mockMvc.perform(get("/api/patient/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("patient@velocura.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.bloodGroup").value("O+"));
    }

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testUpdateProfileSuccess() throws Exception {
        UpdatePatientProfileRequest updateRequest = UpdatePatientProfileRequest.builder()
                .dateOfBirth(LocalDate.of(1995, 5, 10))
                .gender("Male")
                .phoneNumber("0987654321")
                .bloodGroup("O-")
                .address("456 New St")
                .build();

        PatientProfileResponse updatedProfile = PatientProfileResponse.builder()
                .email("patient@velocura.com")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1995, 5, 10))
                .gender("Male")
                .phoneNumber("0987654321")
                .bloodGroup("O-")
                .address("456 New St")
                .build();

        Mockito.when(patientService.updatePatientProfile(eq("patient@velocura.com"), any(UpdatePatientProfileRequest.class)))
                .thenReturn(updatedProfile);

        mockMvc.perform(put("/api/patient/profile/update")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("0987654321"))
                .andExpect(jsonPath("$.bloodGroup").value("O-"));
    }

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testUpdateProfileValidationFailure() throws Exception {
        // Future date of birth violates @Past validation and phone exceeds size
        UpdatePatientProfileRequest invalidRequest = UpdatePatientProfileRequest.builder()
                .dateOfBirth(LocalDate.now().plusDays(5)) 
                .phoneNumber("12345678901234567890") 
                .build();

        mockMvc.perform(put("/api/patient/profile/update")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void testGetProfileUnauthorized() throws Exception {
        // Accessing without authenticated mock details should return 401 Unauthorized
        mockMvc.perform(get("/api/patient/profile"))
                .andExpect(status().isUnauthorized());
    }
}

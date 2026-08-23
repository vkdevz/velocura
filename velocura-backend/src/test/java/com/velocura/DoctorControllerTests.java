package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.controller.DoctorController;
import com.velocura.dto.CreatePrescriptionRequest;
import com.velocura.dto.DoctorProfileResponse;
import com.velocura.dto.UpdateDoctorProfileRequest;
import com.velocura.security.CustomUserDetailsService;
import com.velocura.security.JwtUtils;
import com.velocura.service.DoctorService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorController.class)
@Import(SecurityConfig.class)
class DoctorControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private com.velocura.service.AuditService auditService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private com.velocura.service.PatientService patientService;

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = "DOCTOR")
    void testGetPatientPassportSuccess() throws Exception {
        com.velocura.dto.PatientPassportDto mockPassport = com.velocura.dto.PatientPassportDto.builder()
                .allergies("Penicillin")
                .medicalHistoryTimeline("[]")
                .build();

        Mockito.when(patientService.getPatientPassportById(2L)).thenReturn(mockPassport);

        mockMvc.perform(get("/api/doctor/patient-passport/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies").value("Penicillin"))
                .andExpect(jsonPath("$.medicalHistoryTimeline").value("[]"));
    }

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = "DOCTOR")
    void testGetProfileSuccess() throws Exception {
        DoctorProfileResponse mockProfile = DoctorProfileResponse.builder()
                .email("doctor@velocura.com")
                .firstName("Sarah")
                .lastName("Smith")
                .specialization("Cardiology")
                .licenseNumber("MED-999")
                .experienceYears(15)
                .biography("Cardiologist profile details")
                .consultationFee(new BigDecimal("200.00"))
                .isVerified(true)
                .build();

        Mockito.when(doctorService.getDoctorProfile("doctor@velocura.com")).thenReturn(mockProfile);

        mockMvc.perform(get("/api/doctor/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("doctor@velocura.com"))
                .andExpect(jsonPath("$.specialization").value("Cardiology"))
                .andExpect(jsonPath("$.consultationFee").value(200.00));
    }

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = "DOCTOR")
    void testUpdateProfileSuccess() throws Exception {
        UpdateDoctorProfileRequest updateRequest = UpdateDoctorProfileRequest.builder()
                .specialization("Neurology")
                .experienceYears(16)
                .biography("Updated neurologist profile details")
                .consultationFee(new BigDecimal("250.00"))
                .build();

        DoctorProfileResponse updatedProfile = DoctorProfileResponse.builder()
                .email("doctor@velocura.com")
                .firstName("Sarah")
                .lastName("Smith")
                .specialization("Neurology")
                .licenseNumber("MED-999")
                .experienceYears(16)
                .biography("Updated neurologist profile details")
                .consultationFee(new BigDecimal("250.00"))
                .isVerified(true)
                .build();

        Mockito.when(doctorService.updateDoctorProfile(eq("doctor@velocura.com"), any(UpdateDoctorProfileRequest.class)))
                .thenReturn(updatedProfile);

        mockMvc.perform(put("/api/doctor/profile/update")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("Neurology"))
                .andExpect(jsonPath("$.experienceYears").value(16))
                .andExpect(jsonPath("$.consultationFee").value(250.00));
    }

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = "DOCTOR")
    void testUpdateProfileValidationFailure() throws Exception {
        UpdateDoctorProfileRequest invalidRequest = UpdateDoctorProfileRequest.builder()
                .specialization("") // Violates @NotBlank
                .experienceYears(-1) // Violates @Min(0)
                .consultationFee(new BigDecimal("-10.00")) // Violates @DecimalMin("0.0")
                .build();

        mockMvc.perform(put("/api/doctor/profile/update")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = "DOCTOR")
    void testIssuePrescriptionSuccess() throws Exception {
        CreatePrescriptionRequest request = CreatePrescriptionRequest.builder()
                .appointmentId(1L)
                .patientId(2L)
                .medication("Aspirin 81mg")
                .dosage("Once daily")
                .instructions("Take after meals")
                .build();

        mockMvc.perform(post("/api/doctor/prescriptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Prescription issued successfully!"));
    }

    @Test
    void testGetProfileUnauthorized() throws Exception {
        mockMvc.perform(get("/api/doctor/profile"))
                .andExpect(status().isUnauthorized());
    }
}

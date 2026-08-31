package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.chat.controller.ConversationController;
import com.velocura.chat.controller.PrescriptionController;
import com.velocura.chat.dto.ConversationResponse;
import com.velocura.chat.dto.CreateConversationRequest;
import com.velocura.chat.dto.PrescriptionItemRequest;
import com.velocura.chat.dto.PrescriptionRequest;
import com.velocura.chat.entity.Conversation;
import com.velocura.chat.entity.ConversationStatus;
import com.velocura.chat.entity.Prescription;
import com.velocura.chat.repository.ConversationRepository;
import com.velocura.chat.repository.PrescriptionRepository;
import com.velocura.chat.service.ChatService;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.UserRepository;
import com.velocura.security.CustomUserDetailsService;
import com.velocura.security.JwtUtils;
import com.velocura.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ConversationController.class, PrescriptionController.class})
@Import(SecurityConfig.class)
class ChatSystemIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ConversationRepository conversationRepository;

    @MockBean(name = "chatPrescriptionRepository")
    private PrescriptionRepository chatPrescriptionRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.velocura.chat.config.FileUploadConfig fileUploadConfig;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private com.velocura.service.AuditService auditService;

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testCreateOrGetConversation() throws Exception {
        CreateConversationRequest req = CreateConversationRequest.builder()
                .appointmentId(101L)
                .patientId(1L)
                .doctorId(2L)
                .triageContext("{\"riskLevel\":\"LOW\"}")
                .build();

        Conversation mockConv = new Conversation();
        mockConv.setId(1L);
        mockConv.setAppointmentId(101L);
        mockConv.setPatientId(1L);
        mockConv.setDoctorId(2L);
        mockConv.setStatus(ConversationStatus.ACTIVE);

        ConversationResponse response = ConversationResponse.builder()
                .id(1L)
                .appointmentId(101L)
                .patientId(1L)
                .doctorId(2L)
                .status(ConversationStatus.ACTIVE)
                .triageContext("{\"riskLevel\":\"LOW\"}")
                .build();

        Mockito.when(chatService.getOrCreateConversation(any())).thenReturn(mockConv);
        Mockito.when(chatService.buildConversationResponse(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/conversations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.appointmentId").value(101));
    }

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = "DOCTOR")
    void testDoctorIssuePrescription() throws Exception {
        User doctorUser = User.builder()
                .id(2L)
                .email("doctor@velocura.com")
                .role(Role.DOCTOR)
                .build();

        Conversation mockConv = new Conversation();
        mockConv.setId(1L);
        mockConv.setAppointmentId(101L);
        mockConv.setPatientId(1L);
        mockConv.setDoctorId(2L);
        mockConv.setStatus(ConversationStatus.ACTIVE);

        PrescriptionRequest req = PrescriptionRequest.builder()
                .conversationId(1L)
                .diagnosis("Acute Bronchitis")
                .notes("Rest and hydration")
                .items(List.of(
                        PrescriptionItemRequest.builder()
                                .medicineName("Amoxicillin")
                                .dosage("500mg")
                                .frequency("Twice daily")
                                .duration("7 days")
                                .instructions("After food")
                                .build()
                ))
                .build();

        Prescription savedPrescription = new Prescription();
        savedPrescription.setId(55L);
        savedPrescription.setConversationId(1L);
        savedPrescription.setDoctorId(2L);
        savedPrescription.setPatientId(1L);
        savedPrescription.setAppointmentId(101L);
        savedPrescription.setDiagnosis("Acute Bronchitis");
        savedPrescription.setIssuedAt(LocalDateTime.now());

        Mockito.when(userRepository.findByEmailIgnoreCase("doctor@velocura.com")).thenReturn(Optional.of(doctorUser));
        Mockito.when(conversationRepository.findById(1L)).thenReturn(Optional.of(mockConv));
        Mockito.when(chatPrescriptionRepository.save(any(Prescription.class))).thenReturn(savedPrescription);

        mockMvc.perform(post("/api/prescriptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.diagnosis").value("Acute Bronchitis"));
    }

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testPatientCannotIssuePrescription() throws Exception {
        User patientUser = User.builder()
                .id(1L)
                .email("patient@velocura.com")
                .role(Role.PATIENT)
                .build();

        PrescriptionRequest req = PrescriptionRequest.builder()
                .conversationId(1L)
                .diagnosis("Self Diagnosis")
                .build();

        Mockito.when(userRepository.findByEmailIgnoreCase("patient@velocura.com")).thenReturn(Optional.of(patientUser));

        mockMvc.perform(post("/api/prescriptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}

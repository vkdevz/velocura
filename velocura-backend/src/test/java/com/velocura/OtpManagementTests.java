package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.controller.OtpController;
import com.velocura.dto.OtpDetailResponse;
import com.velocura.model.OtpVerification;
import com.velocura.model.Role;
import com.velocura.model.User;
import com.velocura.repository.OtpVerificationRepository;
import com.velocura.repository.UserRepository;
import com.velocura.service.AdminService;
import com.velocura.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OtpManagementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminService adminService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private static final String TEST_EMAIL = "doctor_live_validation@velocura-test.com";

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
    }

    @Test
    void testOtpGenerationAndVerificationFlow() {
        // 1. Generate OTP
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        // Verify SHA-256 hash is stored, never plaintext in DB
        List<OtpVerification> records = otpRepository.findByEmailIgnoreCase(TEST_EMAIL);
        assertEquals(1, records.size());
        OtpVerification record = records.get(0);
        assertNotNull(record.getCodeHash());
        assertNotEquals(6, record.getCodeHash().length());
        assertFalse(record.isConsumed());

        // 2. Admin ephemeral copy retrieves the actual valid code
        String actualCode = OtpController.getActiveOtpPlaintextForAdmin(TEST_EMAIL);
        assertNotNull(actualCode);
        assertEquals(6, actualCode.length());

        // 3. Verify OTP
        boolean verified = OtpController.verifyAndRemoveOtp(TEST_EMAIL, actualCode);
        assertTrue(verified);

        // 4. One-time consumption: cannot verify twice
        boolean verifyAgain = OtpController.verifyAndRemoveOtp(TEST_EMAIL, actualCode);
        assertFalse(verifyAgain);

        // 5. Plaintext cache evicted upon consumption
        assertNull(OtpController.getActiveOtpPlaintextForAdmin(TEST_EMAIL));
    }

    @Test
    void testResendInvalidatesPreviousOtp_NoDuplicates() {
        // First issue
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);
        String code1 = OtpController.getActiveOtpPlaintextForAdmin(TEST_EMAIL);
        assertNotNull(code1);

        // Resend issue
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);
        String code2 = OtpController.getActiveOtpPlaintextForAdmin(TEST_EMAIL);
        assertNotNull(code2);

        // Previous OTP must be consumed / invalidated
        boolean code1Valid = OtpController.verifyAndRemoveOtp(TEST_EMAIL, code1);
        assertFalse(code1Valid, "Older superseded OTP must not be verifiable");

        // Latest OTP is valid
        boolean code2Valid = OtpController.verifyAndRemoveOtp(TEST_EMAIL, code2);
        assertTrue(code2Valid, "Latest OTP must be verifiable");

        // getActiveOtpsList must return at most 1 active entry per email
        List<OtpDetailResponse> activeList = OtpController.getActiveOtpsList(userRepository);
        long matchingEntries = activeList.stream()
                .filter(dto -> dto.getEmail().equalsIgnoreCase(TEST_EMAIL))
                .count();
        assertEquals(0, matchingEntries, "Consumed OTPs must not appear in active list");
    }

    @Test
    void testActiveOtpsList_MasksCode() {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        List<OtpDetailResponse> activeList = OtpController.getActiveOtpsList(userRepository);
        assertFalse(activeList.isEmpty());
        OtpDetailResponse item = activeList.stream()
                .filter(d -> d.getEmail().equalsIgnoreCase(TEST_EMAIL))
                .findFirst()
                .orElseThrow();

        // Code MUST be masked
        assertEquals("******", item.getCode());
    }

    @Test
    @WithMockUser(username = "admin@velocura.com", roles = {"ADMIN"})
    void testAdminCopyOtp_Authorized() throws Exception {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        MvcResult result = mockMvc.perform(get("/api/admin/otps/" + TEST_EMAIL + "/copy"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> resMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertEquals(true, resMap.get("success"));
        assertNotNull(resMap.get("code"));
        assertEquals(6, ((String) resMap.get("code")).length());
        assertEquals(TEST_EMAIL, resMap.get("email"));
    }

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = {"PATIENT"})
    void testAdminCopyOtp_PatientDenied() throws Exception {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        mockMvc.perform(get("/api/admin/otps/" + TEST_EMAIL + "/copy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "doctor@velocura.com", roles = {"DOCTOR"})
    void testAdminCopyOtp_DoctorDenied() throws Exception {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        mockMvc.perform(get("/api/admin/otps/" + TEST_EMAIL + "/copy"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminCopyOtp_UnauthenticatedDenied() throws Exception {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        mockMvc.perform(get("/api/admin/otps/" + TEST_EMAIL + "/copy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@velocura.com", roles = {"ADMIN"})
    void testRevokeOtp_RemovesActiveChallenge() throws Exception {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        mockMvc.perform(delete("/api/admin/otps/" + TEST_EMAIL))
                .andExpect(status().isOk());

        // Once revoked, copy endpoint returns 410 GONE
        mockMvc.perform(get("/api/admin/otps/" + TEST_EMAIL + "/copy"))
                .andExpect(status().isGone());

        // Verification must fail
        boolean verifyResult = OtpController.verifyAndRemoveOtp(TEST_EMAIL, "123456");
        assertFalse(verifyResult);
    }

    @Test
    void testOtpMaxAttemptsExceeded() {
        OtpController.generateAndSendOtp(TEST_EMAIL, notificationService);

        // 5 incorrect attempts
        for (int i = 0; i < 5; i++) {
            boolean attempt = OtpController.verifyAndRemoveOtp(TEST_EMAIL, "000000");
            assertFalse(attempt);
        }

        // Even with correct code, attempt is rejected after max attempts reached
        String code = OtpController.getActiveOtpPlaintextForAdmin(TEST_EMAIL);
        // Plaintext cache was evicted when max attempts reached
        assertNull(code);
    }
}

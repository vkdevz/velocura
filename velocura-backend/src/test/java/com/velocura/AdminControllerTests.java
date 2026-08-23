package com.velocura;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.controller.AdminController;
import com.velocura.dto.AdminDashboardStatsResponse;
import com.velocura.dto.UserResponse;
import com.velocura.model.Role;
import com.velocura.security.CustomUserDetailsService;
import com.velocura.security.JwtUtils;
import com.velocura.service.AdminService;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtUtils jwtUtils;

    @Test
    @WithMockUser(username = "admin@velocura.com", roles = "ADMIN")
    void testGetDashboardStatsSuccess() throws Exception {
        AdminDashboardStatsResponse mockStats = AdminDashboardStatsResponse.builder()
                .patientCount(150L)
                .doctorCount(40L)
                .appointmentCount(600L)
                .pendingVerificationsCount(5L)
                .build();

        Mockito.when(adminService.getDashboardStats()).thenReturn(mockStats);

        mockMvc.perform(get("/api/admin/dashboard-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCount").value(150))
                .andExpect(jsonPath("$.doctorCount").value(40))
                .andExpect(jsonPath("$.appointmentCount").value(600))
                .andExpect(jsonPath("$.pendingVerificationsCount").value(5));
    }

    @Test
    @WithMockUser(username = "admin@velocura.com", roles = "ADMIN")
    void testVerifyDoctorSuccess() throws Exception {
        mockMvc.perform(put("/api/admin/doctors/1/verify")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Doctor verified successfully!"));

        Mockito.verify(adminService, Mockito.times(1)).verifyDoctor(1L);
    }

    @Test
    @WithMockUser(username = "admin@velocura.com", roles = "ADMIN")
    void testGetAllUsersSuccess() throws Exception {
        List<UserResponse> mockUsers = List.of(
                UserResponse.builder().id(1L).email("user1@test.com").role(Role.PATIENT).isActive(true).build(),
                UserResponse.builder().id(2L).email("user2@test.com").role(Role.DOCTOR).isActive(true).build()
        );

        Mockito.when(adminService.getAllUsers()).thenReturn(mockUsers);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].email").value("user1@test.com"))
                .andExpect(jsonPath("$[1].role").value("DOCTOR"));
    }

    @Test
    @WithMockUser(username = "patient@velocura.com", roles = "PATIENT")
    void testGetAllUsersForbiddenForPatients() throws Exception {
        // Patient trying to hit admin endpoint should return 403 Forbidden
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetAllUsersUnauthorizedAnonymously() throws Exception {
        // Anonymous client should return 401 Unauthorized
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}

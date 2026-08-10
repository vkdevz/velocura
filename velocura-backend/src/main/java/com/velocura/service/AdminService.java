package com.velocura.service;

import com.velocura.dto.AdminDashboardStatsResponse;
import com.velocura.dto.UserResponse;

import java.util.List;

public interface AdminService {
    List<UserResponse> getAllUsers();
    void verifyDoctor(Long doctorId);
    AdminDashboardStatsResponse getDashboardStats();
    List<com.velocura.dto.DoctorProfileResponse> getUnverifiedDoctors();
    void toggleUserActive(Long userId);
    void deleteUser(Long userId);
    List<com.velocura.dto.OtpDetailResponse> getActiveOtps();
    String issueOtp(String email);
    String resendOtp(String email);
    boolean revokeOtp(String email);
}

package com.velocura.service;

import com.velocura.dto.AdminDashboardStatsResponse;
import com.velocura.dto.UserResponse;
import com.velocura.exception.ResourceNotFoundException;
import com.velocura.model.Doctor;
import com.velocura.model.User;
import com.velocura.repository.AppointmentRepository;
import com.velocura.repository.DoctorRepository;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;
    
    @Autowired
    private com.velocura.repository.PrescriptionRepository prescriptionRepository;

    @Autowired
    private com.velocura.repository.MedicalHistoryRepository medicalHistoryRepository;

    @Autowired
    public AdminServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
    }

    @Override
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .role(u.getRole())
                        .isActive(u.isActive())
                        .isDeleted(u.isDeleted())
                        .otp(com.velocura.controller.OtpController.getActiveOtp(
                                u.getEmail() != null && u.getEmail().contains("_deleted_") 
                                ? u.getEmail().split("_deleted_")[0] 
                                : u.getEmail()
                        ))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void verifyDoctor(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with ID: " + doctorId));
        
        doctor.setVerified(true);
        doctorRepository.save(doctor);

        // Send activation verification email
        notificationService.sendDoctorVerificationEmail(
                doctor.getUser().getEmail(),
                doctor.getUser().getFirstName() + " " + doctor.getUser().getLastName()
        );
    }

    @Override
    public AdminDashboardStatsResponse getDashboardStats() {
        long patientCount = patientRepository.count();
        long doctorCount = doctorRepository.count();
        long appointmentCount = appointmentRepository.count();
        long pendingVerificationsCount = doctorRepository.countByIsVerified(false);

        return AdminDashboardStatsResponse.builder()
                .patientCount(patientCount)
                .doctorCount(doctorCount)
                .appointmentCount(appointmentCount)
                .pendingVerificationsCount(pendingVerificationsCount)
                .build();
    }

    @Override
    public List<com.velocura.dto.DoctorProfileResponse> getUnverifiedDoctors() {
        return doctorRepository.findByIsVerified(false).stream()
                .map(d -> com.velocura.dto.DoctorProfileResponse.builder()
                        .id(d.getId())
                        .email(d.getUser().getEmail())
                        .firstName(d.getUser().getFirstName())
                        .lastName(d.getUser().getLastName())
                        .specialization(d.getSpecialization())
                        .licenseNumber(d.getLicenseNumber())
                        .experienceYears(d.getExperienceYears())
                        .biography(d.getBiography())
                        .consultationFee(d.getConsultationFee())
                        .isVerified(d.isVerified())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void toggleUserActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        if (user.getRole() == com.velocura.model.Role.ADMIN) {
            throw new IllegalArgumentException("Administrative accounts cannot be deactivated.");
        }
        user.setActive(!user.isActive());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        if (user.getRole() == com.velocura.model.Role.ADMIN) {
            throw new IllegalArgumentException("Administrative accounts cannot be deleted.");
        }

        // Soft delete user: deactivate and flag deleted
        user.setDeleted(true);
        user.setActive(false);
        
        // Append unique timestamp suffix to free up email address for reuse while maintaining history
        if (!user.getEmail().contains("_deleted_")) {
            user.setEmail(user.getEmail() + "_deleted_" + System.currentTimeMillis());
        }
        
        userRepository.save(user);
    }

    @Override
    public List<com.velocura.dto.OtpDetailResponse> getActiveOtps() {
        return com.velocura.controller.OtpController.getActiveOtpsList(userRepository);
    }

    @Override
    public String issueOtp(String email) {
        return com.velocura.controller.OtpController.issueOtpForAdmin(email, notificationService);
    }

    @Override
    public String resendOtp(String email) {
        return com.velocura.controller.OtpController.issueOtpForAdmin(email, notificationService);
    }

    @Override
    public boolean revokeOtp(String email) {
        return com.velocura.controller.OtpController.revokeOtp(email);
    }
}

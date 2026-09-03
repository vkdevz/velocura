package com.velocura.service;

import com.velocura.dto.MedicalHistoryResponse;
import com.velocura.dto.PatientProfileResponse;
import com.velocura.dto.PrescriptionResponse;
import com.velocura.dto.UpdatePatientProfileRequest;
import com.velocura.dto.DoctorProfileResponse;
import com.velocura.dto.PatientPassportDto;
import com.velocura.dto.VitalsDto;

import com.velocura.dto.ChatHistoryDto;
import com.velocura.dto.SaveChatHistoryRequest;

import java.util.List;

public interface PatientService {
    PatientProfileResponse getPatientProfile(String email);
    PatientProfileResponse updatePatientProfile(String email, UpdatePatientProfileRequest request);
    List<MedicalHistoryResponse> getMedicalHistory(String email);
    List<PrescriptionResponse> getPrescriptions(String email);
    List<DoctorProfileResponse> getVerifiedDoctors();
    PatientPassportDto getPatientPassport(String email);
    PatientPassportDto updatePatientPassport(String email, PatientPassportDto request);
    PatientPassportDto getPatientPassportById(Long patientId);
    List<VitalsDto> getVitals(String email);
    VitalsDto addVitals(String email, VitalsDto request);

    List<ChatHistoryDto> getChatHistory(String email);
    ChatHistoryDto getChatHistoryDetail(String email, Long id);
    ChatHistoryDto saveChatSession(String email, SaveChatHistoryRequest request);
}


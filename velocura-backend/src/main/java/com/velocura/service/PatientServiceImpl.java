package com.velocura.service;

import com.velocura.dto.DoctorProfileResponse;
import com.velocura.dto.MedicalHistoryResponse;
import com.velocura.dto.PatientProfileResponse;
import com.velocura.dto.PrescriptionResponse;
import com.velocura.dto.UpdatePatientProfileRequest;
import com.velocura.dto.PatientPassportDto;
import com.velocura.dto.VitalsDto;
import com.velocura.dto.ChatHistoryDto;
import com.velocura.dto.SaveChatHistoryRequest;
import com.velocura.exception.ResourceNotFoundException;
import com.velocura.model.MedicalHistory;
import com.velocura.model.Patient;
import com.velocura.model.Prescription;
import com.velocura.model.ChatHistorySession;
import com.velocura.model.User;
import com.velocura.model.Vitals;
import com.velocura.repository.DoctorRepository;
import com.velocura.repository.MedicalHistoryRepository;
import com.velocura.repository.PatientRepository;
import com.velocura.repository.PrescriptionRepository;
import com.velocura.repository.UserRepository;
import com.velocura.repository.VitalsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PatientServiceImpl implements PatientService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final DoctorRepository doctorRepository;
    private final VitalsRepository vitalsRepository;
    private final com.velocura.repository.ChatHistorySessionRepository chatHistorySessionRepository;

    @Autowired
    public PatientServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            MedicalHistoryRepository medicalHistoryRepository,
            PrescriptionRepository prescriptionRepository,
            DoctorRepository doctorRepository,
            VitalsRepository vitalsRepository,
            com.velocura.repository.ChatHistorySessionRepository chatHistorySessionRepository) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.medicalHistoryRepository = medicalHistoryRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.doctorRepository = doctorRepository;
        this.vitalsRepository = vitalsRepository;
        this.chatHistorySessionRepository = chatHistorySessionRepository;
    }


    private Patient fetchPatientByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return patientRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user ID: " + user.getId()));
    }

    @Override
    public PatientProfileResponse getPatientProfile(String email) {
        Patient patient = fetchPatientByEmail(email);
        User user = patient.getUser();
        return mapToProfileResponse(user, patient);
    }

    @Override
    @Transactional
    public PatientProfileResponse updatePatientProfile(String email, UpdatePatientProfileRequest request) {
        Patient patient = fetchPatientByEmail(email);
        
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhoneNumber(request.getPhoneNumber());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setAddress(request.getAddress());
        
        patientRepository.save(patient);
        return mapToProfileResponse(patient.getUser(), patient);
    }

    @Override
    public List<MedicalHistoryResponse> getMedicalHistory(String email) {
        Patient patient = fetchPatientByEmail(email);
        List<MedicalHistory> histories = medicalHistoryRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId());
        
        return histories.stream()
                .map(h -> MedicalHistoryResponse.builder()
                        .id(h.getId())
                        .diagnosis(h.getDiagnosis())
                        .symptoms(h.getSymptoms())
                        .treatment(h.getTreatment())
                        .recordedAt(h.getRecordedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<PrescriptionResponse> getPrescriptions(String email) {
        Patient patient = fetchPatientByEmail(email);
        List<Prescription> prescriptions = prescriptionRepository.findByPatientIdOrderByIssuedAtDesc(patient.getId());
        
        return prescriptions.stream()
                .map(p -> PrescriptionResponse.builder()
                        .id(p.getId())
                        .doctorName("Dr. " + p.getDoctor().getUser().getFirstName() + " " + p.getDoctor().getUser().getLastName())
                        .doctorSpecialization(p.getDoctor().getSpecialization())
                        .medication(p.getMedication())
                        .dosage(p.getDosage())
                        .instructions(p.getInstructions())
                        .issuedAt(p.getIssuedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<DoctorProfileResponse> getVerifiedDoctors() {
        return doctorRepository.findByIsVerified(true).stream()
                .map(d -> DoctorProfileResponse.builder()
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

    private PatientProfileResponse mapToProfileResponse(User user, Patient patient) {
        return PatientProfileResponse.builder()
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .phoneNumber(patient.getPhoneNumber())
                .bloodGroup(patient.getBloodGroup())
                .address(patient.getAddress())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientPassportDto getPatientPassport(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        Patient patient = patientRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user: " + user.getId()));
        return PatientPassportDto.builder()
                .allergies(patient.getAllergies())
                .medicalHistoryTimeline(patient.getMedicalHistoryTimeline())
                .build();
    }

    @Override
    @Transactional
    public PatientPassportDto updatePatientPassport(String email, PatientPassportDto request) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        Patient patient = patientRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for user: " + user.getId()));
        patient.setAllergies(request.getAllergies());
        patient.setMedicalHistoryTimeline(request.getMedicalHistoryTimeline());
        patientRepository.save(patient);
        return PatientPassportDto.builder()
                .allergies(patient.getAllergies())
                .medicalHistoryTimeline(patient.getMedicalHistoryTimeline())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientPassportDto getPatientPassportById(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found: " + patientId));
        return PatientPassportDto.builder()
                .allergies(patient.getAllergies())
                .medicalHistoryTimeline(patient.getMedicalHistoryTimeline())
                .build();
    }

    @Override
    public List<VitalsDto> getVitals(String email) {
        Patient patient = fetchPatientByEmail(email);
        List<Vitals> vitalsList = vitalsRepository.findByPatientOrderByRecordedAtDesc(patient);
        return vitalsList.stream()
                .map(v -> VitalsDto.builder()
                        .id(v.getId())
                        .systolic(v.getSystolic())
                        .diastolic(v.getDiastolic())
                        .heartRate(v.getHeartRate())
                        .bloodSugar(v.getBloodSugar())
                        .recordedAt(v.getRecordedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VitalsDto addVitals(String email, VitalsDto request) {
        Patient patient = fetchPatientByEmail(email);
        Vitals vitals = Vitals.builder()
                .patient(patient)
                .systolic(request.getSystolic())
                .diastolic(request.getDiastolic())
                .heartRate(request.getHeartRate())
                .bloodSugar(request.getBloodSugar())
                .recordedAt(java.time.LocalDateTime.now())
                .build();
        Vitals saved = vitalsRepository.save(vitals);
        return VitalsDto.builder()
                .id(saved.getId())
                .systolic(saved.getSystolic())
                .diastolic(saved.getDiastolic())
                .heartRate(saved.getHeartRate())
                .bloodSugar(saved.getBloodSugar())
                .recordedAt(saved.getRecordedAt())
                .build();
    }

    @Override
    public List<ChatHistoryDto> getChatHistory(String email) {
        return chatHistorySessionRepository.findByPatientUserEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::mapToChatHistoryDto)
                .collect(Collectors.toList());
    }

    @Override
    public ChatHistoryDto getChatHistoryDetail(String email, Long id) {
        Patient patient = fetchPatientByEmail(email);
        ChatHistorySession session = chatHistorySessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat history session not found with ID: " + id));
        if (!session.getPatient().getId().equals(patient.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized access to chat record");
        }
        return mapToChatHistoryDto(session);
    }

    @Override
    @Transactional
    public ChatHistoryDto saveChatSession(String email, SaveChatHistoryRequest request) {
        Patient patient = fetchPatientByEmail(email);

        ChatHistorySession session = chatHistorySessionRepository.findBySessionId(request.getSessionId())
                .orElseGet(() -> ChatHistorySession.builder()
                        .patient(patient)
                        .sessionId(request.getSessionId())
                        .startedAt(request.getStartedAt() != null ? request.getStartedAt() : java.time.LocalDateTime.now())
                        .build());

        session.setPatient(patient);
        if (request.getFirstMedicalIssue() != null && !request.getFirstMedicalIssue().isBlank()) {
            session.setFirstMedicalIssue(request.getFirstMedicalIssue());
        }
        if (request.getChiefComplaint() != null && !request.getChiefComplaint().isBlank()) {
            session.setChiefComplaint(request.getChiefComplaint());
        }
        if (request.getPrimaryDiagnosis() != null && !request.getPrimaryDiagnosis().isBlank()) {
            session.setPrimaryDiagnosis(request.getPrimaryDiagnosis());
        }
        if (request.getRiskLevel() != null && !request.getRiskLevel().isBlank()) {
            session.setRiskLevel(request.getRiskLevel());
        }
        session.setStatus(request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus() : "COMPLETED");
        if (request.getMessagesJson() != null) {
            session.setMessagesJson(request.getMessagesJson());
        }
        if (request.getTriageResultJson() != null) {
            session.setTriageResultJson(request.getTriageResultJson());
        }
        session.setCompletedAt(request.getCompletedAt() != null ? request.getCompletedAt() : java.time.LocalDateTime.now());

        ChatHistorySession saved = chatHistorySessionRepository.save(session);
        return mapToChatHistoryDto(saved);
    }

    private ChatHistoryDto mapToChatHistoryDto(ChatHistorySession session) {
        return ChatHistoryDto.builder()
                .id(session.getId())
                .sessionId(session.getSessionId())
                .firstMedicalIssue(session.getFirstMedicalIssue())
                .chiefComplaint(session.getChiefComplaint())
                .primaryDiagnosis(session.getPrimaryDiagnosis())
                .riskLevel(session.getRiskLevel())
                .status(session.getStatus())
                .messagesJson(session.getMessagesJson())
                .triageResultJson(session.getTriageResultJson())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}

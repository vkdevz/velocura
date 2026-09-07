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
import com.velocura.model.Appointment;
import com.velocura.model.Doctor;
import com.velocura.model.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
    private final com.velocura.repository.AppointmentRepository appointmentRepository;
    private final com.velocura.chat.repository.PrescriptionRepository chatPrescriptionRepository;
    private final com.velocura.ai.clinical.state.ClinicalStateStore clinicalStateStore;

    @Autowired
    public PatientServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            MedicalHistoryRepository medicalHistoryRepository,
            PrescriptionRepository prescriptionRepository,
            DoctorRepository doctorRepository,
            VitalsRepository vitalsRepository,
            com.velocura.repository.ChatHistorySessionRepository chatHistorySessionRepository,
            com.velocura.repository.AppointmentRepository appointmentRepository,
            @Autowired(required = false) @org.springframework.beans.factory.annotation.Qualifier("chatPrescriptionRepository") com.velocura.chat.repository.PrescriptionRepository chatPrescriptionRepository,
            @Autowired(required = false) com.velocura.ai.clinical.state.ClinicalStateStore clinicalStateStore) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.medicalHistoryRepository = medicalHistoryRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.doctorRepository = doctorRepository;
        this.vitalsRepository = vitalsRepository;
        this.chatHistorySessionRepository = chatHistorySessionRepository;
        this.appointmentRepository = appointmentRepository;
        this.chatPrescriptionRepository = chatPrescriptionRepository;
        this.clinicalStateStore = clinicalStateStore;
    }

    public PatientServiceImpl(
            UserRepository userRepository,
            PatientRepository patientRepository,
            MedicalHistoryRepository medicalHistoryRepository,
            PrescriptionRepository prescriptionRepository,
            DoctorRepository doctorRepository,
            VitalsRepository vitalsRepository,
            com.velocura.repository.ChatHistorySessionRepository chatHistorySessionRepository,
            com.velocura.repository.AppointmentRepository appointmentRepository,
            com.velocura.chat.repository.PrescriptionRepository chatPrescriptionRepository) {
        this(userRepository, patientRepository, medicalHistoryRepository, prescriptionRepository,
             doctorRepository, vitalsRepository, chatHistorySessionRepository, appointmentRepository,
             chatPrescriptionRepository, null);
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
        
        List<PrescriptionResponse> responses = new java.util.ArrayList<>(prescriptions.stream()
                .map(p -> PrescriptionResponse.builder()
                        .id(p.getId())
                        .doctorName(p.getDoctor() != null && p.getDoctor().getUser() != null ?
                                "Dr. " + p.getDoctor().getUser().getFirstName() + " " + p.getDoctor().getUser().getLastName() : "Attending Physician")
                        .doctorSpecialization(p.getDoctor() != null ? p.getDoctor().getSpecialization() : "General Practice")
                        .medication(p.getMedication())
                        .dosage(p.getDosage())
                        .instructions(p.getInstructions())
                        .issuedAt(p.getIssuedAt())
                        .build())
                .collect(Collectors.toList()));

        if (chatPrescriptionRepository != null && patient.getUser() != null) {
            try {
                List<com.velocura.chat.entity.Prescription> chatPrescriptions = chatPrescriptionRepository.findByPatientId(patient.getUser().getId());
                for (com.velocura.chat.entity.Prescription cp : chatPrescriptions) {
                    String doctorName = "Attending Physician";
                    String specialization = "Telehealth Consultation";
                    if (cp.getDoctorId() != null) {
                        User docUser = userRepository.findById(cp.getDoctorId()).orElse(null);
                        if (docUser != null) {
                            doctorName = "Dr. " + docUser.getFirstName() + " " + docUser.getLastName();
                            Doctor docProfile = doctorRepository.findById(docUser.getId()).orElse(null);
                            if (docProfile != null && docProfile.getSpecialization() != null) {
                                specialization = docProfile.getSpecialization();
                            }
                        }
                    }

                    String medication = cp.getItems() != null && !cp.getItems().isEmpty() ?
                            cp.getItems().stream().map(com.velocura.chat.entity.PrescriptionItem::getMedicineName).collect(Collectors.joining(", ")) :
                            (cp.getDiagnosis() != null ? cp.getDiagnosis() : "Prescribed Regimen");

                    String dosage = cp.getItems() != null && !cp.getItems().isEmpty() ?
                            cp.getItems().stream()
                                    .map(i -> (i.getDosage() != null ? i.getDosage() : "") + (i.getFrequency() != null ? " - " + i.getFrequency() : ""))
                                    .collect(Collectors.joining("; ")) : "";

                    String instructions = cp.getNotes() != null && !cp.getNotes().isBlank() ?
                            cp.getNotes() : (cp.getDiagnosis() != null ? "Diagnosis: " + cp.getDiagnosis() : "Follow physician instructions.");

                    responses.add(PrescriptionResponse.builder()
                            .id(cp.getId())
                            .doctorName(doctorName)
                            .doctorSpecialization(specialization)
                            .medication(medication)
                            .dosage(dosage)
                            .instructions(instructions)
                            .issuedAt(cp.getIssuedAt())
                            .build());
                }
            } catch (Exception e) {
                // Ignore chat prescription mapping errors to ensure resilience
            }
        }

        // Sort unified prescriptions descending by issuedAt
        responses.sort((a, b) -> {
            if (a.getIssuedAt() == null && b.getIssuedAt() == null) return 0;
            if (a.getIssuedAt() == null) return 1;
            if (b.getIssuedAt() == null) return -1;
            return b.getIssuedAt().compareTo(a.getIssuedAt());
        });

        return responses;
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
    @Transactional(readOnly = true)
    public PatientPassportDto getPatientPassportForDoctor(String doctorEmail, Long patientId) {
        User user = userRepository.findByEmailIgnoreCase(doctorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + doctorEmail));

        // ADMIN has system-level clinical access; DOCTOR must have an active or previous appointment/consultation relationship
        if (user.getRole() != Role.ADMIN) {
            Doctor doctor = doctorRepository.findById(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for user ID: " + user.getId()));

            List<Appointment> appointments = appointmentRepository.findByPatientId(patientId);
            boolean hasRelationship = appointments.stream()
                    .anyMatch(a -> a.getDoctor().getId().equals(doctor.getId()));

            if (!hasRelationship) {
                throw new AccessDeniedException("Access Denied: You do not have an active or scheduled clinical relationship with this patient.");
            }
        }

        return getPatientPassportById(patientId);
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

        if (clinicalStateStore != null) {
            try {
                com.velocura.ai.clinical.state.ClinicalConversationState state =
                        clinicalStateStore.findLatestByPatientId(patient.getId());
                if (state != null) {
                    if (state.getVitals() == null) {
                        state.setVitals(new java.util.LinkedHashMap<>());
                    }
                    if (saved.getSystolic() != null) state.getVitals().put("systolic", String.valueOf(saved.getSystolic()));
                    if (saved.getDiastolic() != null) state.getVitals().put("diastolic", String.valueOf(saved.getDiastolic()));
                    if (saved.getHeartRate() != null) state.getVitals().put("heartRate", String.valueOf(saved.getHeartRate()));
                    if (saved.getBloodSugar() != null) state.getVitals().put("bloodSugar", String.valueOf(saved.getBloodSugar()));
                    if (saved.getSystolic() != null && saved.getDiastolic() != null) {
                        state.getVitals().put("bloodPressure", saved.getSystolic() + "/" + saved.getDiastolic());
                    }

                    // Check for acute vitals derangements
                    boolean hypertensiveCrisis = (saved.getSystolic() != null && saved.getSystolic() >= 180) ||
                                                 (saved.getDiastolic() != null && saved.getDiastolic() >= 120);
                    boolean severeTachycardia = saved.getHeartRate() != null && saved.getHeartRate() >= 140;
                    boolean severeBradycardia = saved.getHeartRate() != null && saved.getHeartRate() <= 40 && saved.getHeartRate() > 0;
                    boolean severeHypoglycemia = saved.getBloodSugar() != null && saved.getBloodSugar() <= 54 && saved.getBloodSugar() > 0;
                    boolean severeHyperglycemia = saved.getBloodSugar() != null && saved.getBloodSugar() >= 400;

                    if (hypertensiveCrisis || severeTachycardia || severeBradycardia || severeHypoglycemia || severeHyperglycemia) {
                        String alertReason = hypertensiveCrisis ? "Hypertensive crisis (BP: " + saved.getSystolic() + "/" + saved.getDiastolic() + ")"
                                : severeTachycardia ? "Severe tachycardia (HR: " + saved.getHeartRate() + " bpm)"
                                : severeBradycardia ? "Severe symptomatic bradycardia (HR: " + saved.getHeartRate() + " bpm)"
                                : severeHypoglycemia ? "Severe hypoglycemia (Blood sugar: " + saved.getBloodSugar() + " mg/dL)"
                                : "Severe hyperglycemia (Blood sugar: " + saved.getBloodSugar() + " mg/dL)";

                        com.velocura.ai.clinical.state.StateChangeDiff diff = com.velocura.ai.clinical.state.StateChangeDiff.builder()
                                .triggerTurnInput("Vitals Entry: " + alertReason)
                                .riskTransition(state.getCurrentRiskLevel() + " -> CRITICAL")
                                .addedFacts(java.util.List.of("Vitals Alert: " + alertReason))
                                .build();

                        state.setCurrentRiskLevel(com.velocura.ai.clinical.state.ClinicalRiskLevel.CRITICAL);
                        state.setRecommendedAction(com.velocura.ai.clinical.state.NextAction.EMERGENCY_ESCALATION);
                        state.setCurrentPhase(com.velocura.ai.clinical.state.ClinicalPhase.ESCALATION);
                        if (state.getRedFlags() == null) state.setRedFlags(new java.util.ArrayList<>());
                        state.getRedFlags().add("CRITICAL_VITALS: " + alertReason);
                        state.recordStateChange(diff);
                    } else {
                        com.velocura.ai.clinical.state.StateChangeDiff diff = com.velocura.ai.clinical.state.StateChangeDiff.builder()
                                .triggerTurnInput("Vitals Entry recorded")
                                .addedFacts(java.util.List.of("Vitals updated"))
                                .build();
                        state.recordStateChange(diff);
                    }
                    clinicalStateStore.save(state);
                }
            } catch (Exception e) {
                // Non-blocking for vitals capture
            }
        }

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

        Optional<ChatHistorySession> existingOpt = chatHistorySessionRepository.findBySessionId(request.getSessionId());
        ChatHistorySession session;
        if (existingOpt.isPresent()) {
            session = existingOpt.get();
            if (session.getPatient() != null && !session.getPatient().getId().equals(patient.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Unauthorized: You cannot update or overwrite a clinical session belonging to another patient.");
            }
        } else {
            session = ChatHistorySession.builder()
                    .patient(patient)
                    .sessionId(request.getSessionId())
                    .startedAt(request.getStartedAt() != null ? request.getStartedAt() : java.time.LocalDateTime.now())
                    .build();
        }
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

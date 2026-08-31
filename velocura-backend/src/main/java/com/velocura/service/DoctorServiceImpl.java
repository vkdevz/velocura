package com.velocura.service;

import com.velocura.dto.*;
import com.velocura.exception.ResourceNotFoundException;
import com.velocura.model.*;
import com.velocura.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DoctorServiceImpl implements DoctorService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;

    @Autowired
    public DoctorServiceImpl(
            UserRepository userRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            PrescriptionRepository prescriptionRepository,
            MedicalHistoryRepository medicalHistoryRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.medicalHistoryRepository = medicalHistoryRepository;
    }

    private Doctor fetchDoctorByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return doctorRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile not found for user ID: " + user.getId()));
    }

    @Override
    public DoctorProfileResponse getDoctorProfile(String email) {
        Doctor doctor = fetchDoctorByEmail(email);
        User user = doctor.getUser();
        return mapToProfileResponse(user, doctor);
    }

    @Override
    @Transactional
    public DoctorProfileResponse updateDoctorProfile(String email, UpdateDoctorProfileRequest request) {
        Doctor doctor = fetchDoctorByEmail(email);
        
        doctor.setSpecialization(request.getSpecialization());
        doctor.setExperienceYears(request.getExperienceYears());
        doctor.setBiography(request.getBiography());
        doctor.setConsultationFee(request.getConsultationFee());
        
        doctorRepository.save(doctor);
        return mapToProfileResponse(doctor.getUser(), doctor);
    }

    @Override
    public List<DoctorAppointmentResponse> getDoctorAppointments(String email) {
        Doctor doctor = fetchDoctorByEmail(email);
        List<Appointment> appointments = appointmentRepository.findByDoctorId(doctor.getId());
        
        return appointments.stream()
                .map(a -> DoctorAppointmentResponse.builder()
                        .appointmentId(a.getId())
                        .patientId(a.getPatient().getId())
                        .patientName(a.getPatient().getUser().getFirstName() + " " + a.getPatient().getUser().getLastName())
                        .appointmentTime(a.getAppointmentTime())
                        .status(a.getStatus())
                        .reason(a.getReason())
                        .notes(a.getNotes())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void issuePrescription(String email, CreatePrescriptionRequest request) {
        Doctor doctor = fetchDoctorByEmail(email);
        
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with ID: " + request.getPatientId()));
        
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with ID: " + request.getAppointmentId()));
        
        // Security check: Verify this appointment belongs to the active doctor
        if (!appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new AccessDeniedException("Unauthorized: You are not the scheduled doctor for this appointment.");
        }

        Prescription prescription = Prescription.builder()
                .appointment(appointment)
                .patient(patient)
                .doctor(doctor)
                .medication(request.getMedication())
                .dosage(request.getDosage())
                .instructions(request.getInstructions())
                .build();

        prescriptionRepository.save(prescription);

        // Mark appointment as COMPLETED upon issuing prescription
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
    }

    @Override
    @Transactional
    public void addMedicalHistory(String email, AddMedicalHistoryRequest request) {
        Doctor doctor = fetchDoctorByEmail(email);
        
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with ID: " + request.getPatientId()));

        // Security check: Verify the doctor has consulted this patient before (relationship exists)
        List<Appointment> appointments = appointmentRepository.findByPatientId(patient.getId());
        boolean hasRelationship = appointments.stream()
                .anyMatch(a -> a.getDoctor().getId().equals(doctor.getId()));
        
        if (!hasRelationship) {
            throw new AccessDeniedException("Unauthorized: You must have a scheduled appointment history to update this patient's medical records.");
        }

        MedicalHistory history = MedicalHistory.builder()
                .patient(patient)
                .diagnosis(request.getDiagnosis())
                .symptoms(request.getSymptoms())
                .treatment(request.getTreatment())
                .recordedAt(LocalDate.now())
                .build();

        medicalHistoryRepository.save(history);
    }

    private DoctorProfileResponse mapToProfileResponse(User user, Doctor doctor) {
        return DoctorProfileResponse.builder()
                .id(doctor.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .specialization(doctor.getSpecialization())
                .licenseNumber(doctor.getLicenseNumber())
                .experienceYears(doctor.getExperienceYears())
                .biography(doctor.getBiography())
                .consultationFee(doctor.getConsultationFee())
                .isVerified(doctor.isVerified())
                .build();
    }
}

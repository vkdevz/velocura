package com.velocura.repository;

import com.velocura.model.ClinicalValidationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalValidationRepository extends JpaRepository<ClinicalValidationRecord, Long> {
    List<ClinicalValidationRecord> findByDoctorUserIdOrderByCreatedAtDesc(Long doctorUserId);
    List<ClinicalValidationRecord> findByAppointmentId(Long appointmentId);
    List<ClinicalValidationRecord> findBySessionId(String sessionId);
}

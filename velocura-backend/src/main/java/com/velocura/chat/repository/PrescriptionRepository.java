package com.velocura.chat.repository;

import com.velocura.chat.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("chatPrescriptionRepository")
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByConversationId(Long conversationId);
    List<Prescription> findByPatientId(Long patientId);
    List<Prescription> findByDoctorId(Long doctorId);
}

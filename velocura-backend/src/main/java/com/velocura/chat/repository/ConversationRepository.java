package com.velocura.chat.repository;

import com.velocura.chat.entity.Conversation;
import com.velocura.chat.entity.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByPatientIdOrderByCreatedAtDesc(Long patientId);
    List<Conversation> findByDoctorIdOrderByCreatedAtDesc(Long doctorId);
    Optional<Conversation> findByAppointmentId(Long appointmentId);
    List<Conversation> findByPatientIdAndStatus(Long patientId, ConversationStatus status);
    List<Conversation> findByDoctorIdAndStatus(Long doctorId, ConversationStatus status);
}

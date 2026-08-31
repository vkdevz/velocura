package com.velocura.repository;

import com.velocura.model.ConsultationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {
    List<ConsultationMessage> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);
}

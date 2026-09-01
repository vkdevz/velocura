package com.velocura.repository;

import com.velocura.model.ConsultationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {
    @org.springframework.data.jpa.repository.Query("SELECT m FROM ConsultationMessage m LEFT JOIN FETCH m.sender LEFT JOIN FETCH m.recipient LEFT JOIN FETCH m.appointment WHERE m.appointment.id = :appointmentId ORDER BY m.createdAt ASC")
    List<ConsultationMessage> findByAppointmentIdOrderByCreatedAtAsc(@org.springframework.data.repository.query.Param("appointmentId") Long appointmentId);
}

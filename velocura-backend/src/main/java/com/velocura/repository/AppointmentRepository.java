package com.velocura.repository;

import com.velocura.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientId(Long patientId);
    List<Appointment> findByDoctorId(Long doctorId);
    boolean existsByDoctorIdAndAppointmentTimeAndStatusIn(
            Long doctorId, 
            java.time.LocalDateTime appointmentTime, 
            List<com.velocura.model.AppointmentStatus> statuses
    );

    @org.springframework.data.jpa.repository.Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.doctor d LEFT JOIN FETCH d.user LEFT JOIN FETCH a.patient p LEFT JOIN FETCH p.user WHERE a.id = :id")
    java.util.Optional<Appointment> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") Long id);
}

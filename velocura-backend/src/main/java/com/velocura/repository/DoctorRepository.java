package com.velocura.repository;

import com.velocura.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findBySpecializationIgnoreCase(String specialization);

    @Query("SELECT d FROM Doctor d WHERE d.isVerified = :isVerified")
    List<Doctor> findByIsVerified(@Param("isVerified") boolean isVerified);

    @Query("SELECT COUNT(d) FROM Doctor d WHERE d.isVerified = :isVerified")
    long countByIsVerified(@Param("isVerified") boolean isVerified);
}


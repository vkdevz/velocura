package com.velocura.repository;

import com.velocura.model.Patient;
import com.velocura.model.Vitals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VitalsRepository extends JpaRepository<Vitals, Long> {
    List<Vitals> findByPatientOrderByRecordedAtDesc(Patient patient);
}

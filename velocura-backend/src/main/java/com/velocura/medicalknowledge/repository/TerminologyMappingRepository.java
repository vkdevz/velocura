package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.TerminologyMapping;
import com.velocura.medicalknowledge.model.TerminologySystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TerminologyMappingRepository extends JpaRepository<TerminologyMapping, Long> {

    Optional<TerminologyMapping> findByTerminologySystemAndCode(TerminologySystem system, String code);

    List<TerminologyMapping> findByCode(String code);

    List<TerminologyMapping> findByConceptConceptId(String conceptId);

    List<TerminologyMapping> findByTerminologySystem(TerminologySystem system);
}

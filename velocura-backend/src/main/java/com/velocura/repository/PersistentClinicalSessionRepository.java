package com.velocura.repository;

import com.velocura.model.PersistentClinicalSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PersistentClinicalSessionRepository extends JpaRepository<PersistentClinicalSession, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM PersistentClinicalSession s WHERE s.lastUpdated < :cutoff")
    void deleteByLastUpdatedLessThan(@Param("cutoff") Long cutoff);

    java.util.Optional<PersistentClinicalSession> findTopByPatientIdOrderByLastUpdatedDesc(Long patientId);

    java.util.Optional<PersistentClinicalSession> findTopByPatientEmailIgnoreCaseOrderByLastUpdatedDesc(String email);
}

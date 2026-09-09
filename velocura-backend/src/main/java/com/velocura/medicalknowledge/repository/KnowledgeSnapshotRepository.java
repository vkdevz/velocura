package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.KnowledgeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnowledgeSnapshotRepository extends JpaRepository<KnowledgeSnapshot, String> {

    @Query("SELECT s FROM KnowledgeSnapshot s WHERE s.status = 'ACTIVE' ORDER BY s.createdAt DESC LIMIT 1")
    Optional<KnowledgeSnapshot> findLatestActiveSnapshot();

    Optional<KnowledgeSnapshot> findByChecksum(String checksum);
}

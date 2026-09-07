package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.KnowledgeSource;
import com.velocura.medicalknowledge.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, String> {
    List<KnowledgeSource> findBySourceType(SourceType sourceType);
    List<KnowledgeSource> findByStatus(String status);
    Optional<KnowledgeSource> findByNameIgnoreCase(String name);
}

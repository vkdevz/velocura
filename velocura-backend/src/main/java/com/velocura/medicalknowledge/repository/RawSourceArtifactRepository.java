package com.velocura.medicalknowledge.repository;

import com.velocura.medicalknowledge.model.RawSourceArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RawSourceArtifactRepository extends JpaRepository<RawSourceArtifact, Long> {
    Optional<RawSourceArtifact> findByArtifactId(String artifactId);
    Optional<RawSourceArtifact> findBySourceIdAndVersion(String sourceId, String version);
    List<RawSourceArtifact> findBySourceIdOrderByAcquisitionTimestampDesc(String sourceId);
    boolean existsBySourceIdAndVersion(String sourceId, String version);
}

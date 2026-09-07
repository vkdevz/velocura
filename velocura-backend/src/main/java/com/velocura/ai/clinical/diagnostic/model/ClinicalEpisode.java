package com.velocura.ai.clinical.diagnostic.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * ClinicalEpisode:
 * Maintains episodic boundaries for distinct patient complaints (e.g. separating a
 * headache episode from an acute ankle sprain), preventing cross-contamination while
 * retaining longitudinal patient-level facts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalEpisode implements Serializable {

    private String episodeId;
    private Long patientId;
    private String sessionId;
    private String chiefConcern;

    @Builder.Default
    private EpisodeStatus status = EpisodeStatus.ACTIVE;

    @Builder.Default
    private Map<String, ClinicalDiagnosticFeature> features = new LinkedHashMap<>();

    @Builder.Default
    private Set<String> negatedFeatures = new LinkedHashSet<>();

    @Builder.Default
    private Set<String> unknownFeatures = new LinkedHashSet<>();

    @Builder.Default
    private TrajectoryType trajectory = TrajectoryType.NEW;

    @Builder.Default
    private long startTime = System.currentTimeMillis();

    @Builder.Default
    private long lastUpdated = System.currentTimeMillis();

    public static ClinicalEpisode createNew(String sessionId, Long patientId, String chiefConcern) {
        String epId = "EP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ClinicalEpisode.builder()
                .episodeId(epId)
                .sessionId(sessionId)
                .patientId(patientId)
                .chiefConcern(chiefConcern)
                .status(EpisodeStatus.ACTIVE)
                .startTime(System.currentTimeMillis())
                .lastUpdated(System.currentTimeMillis())
                .build();
    }

    public void addOrUpdateFeature(ClinicalDiagnosticFeature feature) {
        if (feature == null || feature.getConceptId() == null) return;
        if (features == null) features = new LinkedHashMap<>();
        if (negatedFeatures == null) negatedFeatures = new LinkedHashSet<>();
        if (unknownFeatures == null) unknownFeatures = new LinkedHashSet<>();

        String key = feature.getConceptId().toUpperCase();

        if (feature.isPresent()) {
            features.put(key, feature);
            negatedFeatures.remove(key);
            unknownFeatures.remove(key);
        } else if (feature.isDenied()) {
            features.put(key, feature);
            negatedFeatures.add(key);
            unknownFeatures.remove(key);
        } else {
            unknownFeatures.add(key);
        }
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean hasFeature(String conceptId) {
        if (conceptId == null || features == null) return false;
        ClinicalDiagnosticFeature f = features.get(conceptId.toUpperCase());
        return f != null && f.isPresent();
    }

    public boolean isFeatureDenied(String conceptId) {
        if (conceptId == null) return false;
        if (negatedFeatures != null && negatedFeatures.contains(conceptId.toUpperCase())) {
            return true;
        }
        ClinicalDiagnosticFeature f = features != null ? features.get(conceptId.toUpperCase()) : null;
        return f != null && f.isDenied();
    }

    public List<ClinicalDiagnosticFeature> getPresentFeatures() {
        if (features == null) return Collections.emptyList();
        List<ClinicalDiagnosticFeature> list = new ArrayList<>();
        for (ClinicalDiagnosticFeature f : features.values()) {
            if (f.isPresent()) list.add(f);
        }
        return list;
    }
}

package com.velocura.ai.clinical.retrieval.dto;

import com.velocura.ai.clinical.model.ClinicalEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ClinicalCandidate:
 * Represents a candidate medical concept retrieved by the 11,003-entity In-Memory Inverted Index.
 *
 * HARD ARCHITECTURAL INVARIANT:
 * Candidate != Diagnosis.
 * Relevance score != Probability.
 * Retrieval rank != Clinical certainty.
 *
 * The retrieval layer may ONLY produce candidates with relevance scores, matched features,
 * and provenance. It has ZERO diagnostic, treatment, referral, or prescription authority.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalCandidate implements Serializable {

    private String conceptId;
    private String terminologyCode; // e.g. WHO ICD-11 code ("DA00.0", "8A80")
    private String displayName;
    private double relevanceScore; // Lexical/TF-IDF support score. STRICTLY NOT A PROBABILITY.

    @Builder.Default
    private List<String> matchedFeatures = new ArrayList<>();

    @Builder.Default
    private List<String> matchedHallmarks = new ArrayList<>();

    @Builder.Default
    private List<String> contradictions = new ArrayList<>();

    @Builder.Default
    private String provenance = "WHO-ICD11-CORE-11K-INVERTED-INDEX";

    private String knowledgeSnapshotId;

    @Builder.Default
    private List<String> retrievalTrace = new ArrayList<>();

    private ClinicalEntity backingEntity;
}

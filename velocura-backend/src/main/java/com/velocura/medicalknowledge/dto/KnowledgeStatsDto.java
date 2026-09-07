package com.velocura.medicalknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeStatsDto {

    private long totalConcepts;

    @Builder.Default
    private Map<String, Long> conceptsByType = new HashMap<>();

    private long totalRelationships;

    @Builder.Default
    private Map<String, Long> relationshipsByType = new HashMap<>();

    private long totalTerminologyMappings;
    private long totalSources;
    private long activeBatches;
    private long totalSynonyms;
}

package com.velocura.ai.clinical.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscriminatorQuestion {
    private String id;
    private String dimension;
    private String questionText;
    @Builder.Default
    private List<String> quickReplies = new ArrayList<>();
    @Builder.Default
    private Map<String, Double> conditionWeights = new HashMap<>();
    @Builder.Default
    private double diagnosticUtility = 1.0;
}

package com.velocura.ai.clinical.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalEvidence {
    private String topic;
    private String summary;
    private String source;
    @Builder.Default
    private String status = "MEDICALLY_ESTABLISHED";
    @Builder.Default
    private List<String> redFlags = new ArrayList<>();
    @Builder.Default
    private List<String> contraindications = new ArrayList<>();
    @Builder.Default
    private List<String> safeMeasures = new ArrayList<>();
}

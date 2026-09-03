package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalFact implements Serializable {
    private String name;
    private String value;
    @Builder.Default
    private FactStatus status = FactStatus.USER_REPORTED;
    private int sourceTurn;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    public static ClinicalFact userReported(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .status(FactStatus.USER_REPORTED)
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact inferred(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .status(FactStatus.AI_INFERENCE)
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }

    public static ClinicalFact established(String name, String value, int turn) {
        return ClinicalFact.builder()
                .name(name)
                .value(value)
                .status(FactStatus.MEDICALLY_ESTABLISHED)
                .sourceTurn(turn)
                .timestamp(System.currentTimeMillis())
                .attributes(new HashMap<>())
                .build();
    }
}

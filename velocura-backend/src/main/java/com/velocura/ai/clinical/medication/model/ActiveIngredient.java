package com.velocura.ai.clinical.medication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveIngredient implements Serializable {
    private String conceptId;
    private String canonicalName;
    private String atcCode;
    private String rxNormCode;
    @Builder.Default
    private List<String> synonyms = new ArrayList<>();
}

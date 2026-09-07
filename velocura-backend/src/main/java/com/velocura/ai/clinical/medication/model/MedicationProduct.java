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
public class MedicationProduct implements Serializable {
    private String productId;
    private String brandName;
    private String genericName;
    private String dosageForm;
    private String strength;
    private String route;
    @Builder.Default
    private List<ActiveIngredient> activeIngredients = new ArrayList<>();
    @Builder.Default
    private List<String> synonyms = new ArrayList<>();
    private String rxNormCode;
    private String jurisdiction;

    public boolean isCombination() {
        return activeIngredients != null && activeIngredients.size() > 1;
    }
}

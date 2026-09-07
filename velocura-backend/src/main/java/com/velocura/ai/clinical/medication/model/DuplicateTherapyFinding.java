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
public class DuplicateTherapyFinding implements Serializable {
    private String activeIngredient;
    @Builder.Default
    private List<String> medicationsInvolved = new ArrayList<>();
    private String clinicalMessage;
}

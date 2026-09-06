package com.velocura.ai.clinical.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RxMedicationItem {
    private String saltName;
    private String brandReference;
    private String formulation;      // Tablet, Capsule, Oral Solution, Cream, Ointment, Inhaler, Eye Drop
    private String strength;         // 650 mg, 500 mg, 1.16%, etc.
    private String route;            // Oral, Topical, Ophthalmic, Inhalation
    private String dosageFrequency;  // 1 tablet every 6 hours PRN, Twice daily, etc.
    private String duration;         // 3 to 5 days, 7 days, etc.
    private String instructions;     // After meals with full glass of water
    private String indication;       // Antipyretic, Anti-inflammatory, Antibacterial barrier
    private boolean prescriptionOnly; // true = Rx only, false = OTC
}

package com.velocura.ai.clinical.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Explicit representation of conflicting clinical statements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalContradiction implements Serializable {

    private String topic;               // e.g. "medication_history", "fever_presence"
    private String earlierStatement;    // e.g. "No medications"
    private int earlierTurn;
    private String laterStatement;      // e.g. "I took ibuprofen and paracetamol"
    private int laterTurn;
    
    @Builder.Default
    private String status = "REQUIRES_CLARIFICATION"; // REQUIRES_CLARIFICATION, RESOLVED_EARLIER, RESOLVED_LATER
    
    private String resolutionNote;
    
    @Builder.Default
    private long detectedAt = System.currentTimeMillis();
}

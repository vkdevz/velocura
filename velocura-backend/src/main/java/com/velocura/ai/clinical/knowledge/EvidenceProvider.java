package com.velocura.ai.clinical.knowledge;

import java.util.Optional;

public interface EvidenceProvider {
    boolean supports(String topic);
    Optional<ClinicalEvidence> retrieve(String topic);
}

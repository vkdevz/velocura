package com.velocura.ai.clinical.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ClinicalKnowledgeService: Retrieves targeted, traceable evidence from modular providers.
 * Strictly avoids giant hard-coded encyclopedias and only retrieves facts relevant to current turn.
 */
@Service
public class ClinicalKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKnowledgeService.class);

    private final List<EvidenceProvider> providers;

    public ClinicalKnowledgeService(List<EvidenceProvider> providers) {
        this.providers = providers != null ? providers : new ArrayList<>();
    }

    public List<ClinicalEvidence> retrieveEvidence(String text) {
        List<ClinicalEvidence> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        for (EvidenceProvider provider : providers) {
            try {
                if (provider.supports(text)) {
                    Optional<ClinicalEvidence> evidence = provider.retrieve(text);
                    evidence.ifPresent(results::add);
                }
            } catch (Exception e) {
                log.warn("Knowledge provider retrieval error: {}", e.getMessage());
            }
        }
        return results;
    }
}

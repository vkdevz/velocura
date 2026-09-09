package com.velocura.medicalknowledge.service;

import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.KnowledgeConflictRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeConflictEngine {

    private final KnowledgeConflictRecordRepository conflictRepository;

    /**
     * Detects and arbitrates conflicts between two competing clinical claims.
     * Guaranteed never to silently overwrite or synthesize unverified consensus.
     */
    @Transactional
    public KnowledgeConflictRecord arbitrateConflict(
            String subjectConceptId,
            String predicate,
            String objectConceptId,
            String claimASourceId,
            String claimAVersion,
            EvidenceLevel claimAEvidence,
            Jurisdiction claimAJurisdiction,
            String claimAAssertion,
            String claimBSourceId,
            String claimBVersion,
            EvidenceLevel claimBEvidence,
            Jurisdiction claimBJurisdiction,
            String claimBAssertion,
            Jurisdiction targetJurisdiction) {

        String conflictId = "CONF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ConflictStatus status = ConflictStatus.OPEN_CONFLICT;
        ConflictResolutionPolicy appliedPolicy = ConflictResolutionPolicy.UNRESOLVED_UNCERTAIN;
        String winningSource = null;
        String rationale = "Competing claims with insufficient divergence to declare automated truth. Preserved as uncertain.";

        int scoreA = getEvidenceRank(claimAEvidence);
        int scoreB = getEvidenceRank(claimBEvidence);

        // 1. Jurisdictional specificity check
        if (targetJurisdiction != null && targetJurisdiction != Jurisdiction.GLOBAL) {
            if (claimAJurisdiction == targetJurisdiction && claimBJurisdiction != targetJurisdiction) {
                status = ConflictStatus.RESOLVED_BY_JURISDICTION;
                appliedPolicy = ConflictResolutionPolicy.JURISDICTIONAL_OVERRIDE;
                winningSource = claimASourceId;
                rationale = "Claim A explicitly matches target jurisdiction: " + targetJurisdiction;
            } else if (claimBJurisdiction == targetJurisdiction && claimAJurisdiction != targetJurisdiction) {
                status = ConflictStatus.RESOLVED_BY_JURISDICTION;
                appliedPolicy = ConflictResolutionPolicy.JURISDICTIONAL_OVERRIDE;
                winningSource = claimBSourceId;
                rationale = "Claim B explicitly matches target jurisdiction: " + targetJurisdiction;
            }
        }

        // 2. Evidence hierarchy check (if jurisdiction was neutral)
        if (status == ConflictStatus.OPEN_CONFLICT) {
            if (scoreA > scoreB) {
                status = ConflictStatus.RESOLVED_BY_AUTHORITY;
                appliedPolicy = ConflictResolutionPolicy.AUTHORITY_AND_EVIDENCE_HIERARCHY;
                winningSource = claimASourceId;
                rationale = String.format("Claim A evidence (%s) outweighs Claim B (%s)", claimAEvidence, claimBEvidence);
            } else if (scoreB > scoreA) {
                status = ConflictStatus.RESOLVED_BY_AUTHORITY;
                appliedPolicy = ConflictResolutionPolicy.AUTHORITY_AND_EVIDENCE_HIERARCHY;
                winningSource = claimBSourceId;
                rationale = String.format("Claim B evidence (%s) outweighs Claim A (%s)", claimBEvidence, claimAEvidence);
            } else {
                // Evidence rank tied: check recency via version
                int vCompare = compareVersions(claimAVersion, claimBVersion);
                if (vCompare > 0) {
                    status = ConflictStatus.RESOLVED_BY_RECENCY;
                    appliedPolicy = ConflictResolutionPolicy.RECENCY_OF_PUBLICATION;
                    winningSource = claimASourceId;
                    rationale = "Claim A version is more recent than Claim B";
                } else if (vCompare < 0) {
                    status = ConflictStatus.RESOLVED_BY_RECENCY;
                    appliedPolicy = ConflictResolutionPolicy.RECENCY_OF_PUBLICATION;
                    winningSource = claimBSourceId;
                    rationale = "Claim B version is more recent than Claim A";
                } else {
                    status = ConflictStatus.QUARANTINED_UNCERTAIN;
                    appliedPolicy = ConflictResolutionPolicy.UNRESOLVED_UNCERTAIN;
                    winningSource = null;
                    rationale = "Equal evidence tier and identical recency. Maintained as explicit medical uncertainty.";
                }
            }
        }

        KnowledgeConflictRecord record = KnowledgeConflictRecord.builder()
                .conflictId(conflictId)
                .subjectConceptId(subjectConceptId)
                .predicate(predicate)
                .objectConceptId(objectConceptId)
                .claimASourceId(claimASourceId)
                .claimASourceVersion(claimAVersion)
                .claimAEvidenceLevel(claimAEvidence)
                .claimAJurisdiction(claimAJurisdiction)
                .claimAAssertion(claimAAssertion)
                .claimBSourceId(claimBSourceId)
                .claimBSourceVersion(claimBVersion)
                .claimBEvidenceLevel(claimBEvidence)
                .claimBJurisdiction(claimBJurisdiction)
                .claimBAssertion(claimBAssertion)
                .status(status)
                .appliedPolicy(appliedPolicy)
                .winningClaimSourceId(winningSource)
                .resolutionRationale(rationale)
                .detectedAt(LocalDateTime.now())
                .build();

        log.info("[KNOWLEDGE CONFLICT] Conflict {} registered: {} - Winner: {}", conflictId, status, winningSource);
        return conflictRepository.save(record);
    }

    private int getEvidenceRank(EvidenceLevel level) {
        if (level == null) return 0;
        return switch (level) {
            case A -> 5;
            case B -> 4;
            case C -> 3;
            case D -> 2;
            case UNKNOWN -> 1;
        };
    }

    private int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        return v1.compareToIgnoreCase(v2);
    }
}

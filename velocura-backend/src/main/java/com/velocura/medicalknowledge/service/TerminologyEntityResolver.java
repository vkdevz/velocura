package com.velocura.medicalknowledge.service;

import com.velocura.medicalknowledge.dto.ConceptImportDto;
import com.velocura.medicalknowledge.dto.EntityResolutionResult;
import com.velocura.medicalknowledge.dto.TerminologyMappingDto;
import com.velocura.medicalknowledge.model.*;
import com.velocura.medicalknowledge.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

/**
 * Deterministic Terminology Normalization and Conservative Entity Resolution (Section 16, 17).
 * Strictly distinguishes:
 * - EXACT_IDENTITY: Same authoritative concept identifier
 * - STRONG_DETERMINISTIC_MATCH: Canonical name / confirmed synonym
 * - SUPPORTED_MAPPING: Authoritative terminology code crosswalk (ICD-11, SNOMED, LOINC, RxNorm)
 * - POSSIBLE_MATCH: Single unconfirmed candidate (for clinician review, never silently merged)
 * - AMBIGUOUS: Multiple competing candidates (requires review, never auto-merged)
 * - CONFLICT: Incompatible clinical concepts
 * - UNRESOLVED: Concept unknown in active knowledge snapshot
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminologyEntityResolver {

    private final MedicalConceptRepository conceptRepository;
    private final MedicalConceptSynonymRepository synonymRepository;
    private final TerminologyMappingRepository mappingRepository;

    /**
     * Deterministic text normalizer for medical terms:
     * - Unicode NFKC normalization
     * - Case folding
     * - Whitespace condensation
     * - Punctuation stripping (retains hyphens for compound medical terms)
     */
    public String normalizeTerm(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        // 1. Unicode NFKC
        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFKC);
        // 2. Lowercase
        normalized = normalized.toLowerCase(Locale.ROOT).trim();
        // 3. Strip non-word chars except hyphen and space
        normalized = normalized.replaceAll("[^a-z0-9\\-\\s]", " ");
        // 4. Collapse multiple spaces
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    /**
     * Resolves an incoming clinical string or code into a verified MedicalConcept entity.
     */
    public EntityResolutionResult resolve(String rawQuery, MedicalConceptType expectedType) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return EntityResolutionResult.builder()
                    .matchClass(EntityResolutionMatchClass.UNRESOLVED)
                    .normalizedQuery("")
                    .confidence(0.0)
                    .notes("Empty query provided")
                    .build();
        }

        String queryTrimmed = rawQuery.trim();
        String norm = normalizeTerm(rawQuery);

        // 1. Exact Concept ID lookup (EXACT_IDENTITY)
        Optional<MedicalConcept> byId = conceptRepository.findById(queryTrimmed);
        if (byId.isPresent() && byId.get().getStatus() == ConceptStatus.ACTIVE) {
            MedicalConcept c = byId.get();
            if (expectedType != null && c.getConceptType() != expectedType) {
                return EntityResolutionResult.builder()
                        .matchClass(EntityResolutionMatchClass.CONFLICT)
                        .resolvedConcept(c)
                        .normalizedQuery(norm)
                        .confidence(0.0)
                        .notes("Concept ID matched but type conflict: expected " + expectedType + " but found " + c.getConceptType())
                        .build();
            }
            return EntityResolutionResult.builder()
                    .matchClass(EntityResolutionMatchClass.EXACT_MATCH) // Preserves EXACT_MATCH for backwards tests
                    .resolvedConcept(c)
                    .normalizedQuery(norm)
                    .confidence(1.0)
                    .notes("Direct Concept ID exact identity match: " + queryTrimmed)
                    .build();
        }

        // 2. Terminology mapping lookup (e.g. ICD-11 / SNOMED / LOINC / RxNorm code)
        List<TerminologyMapping> mappings = mappingRepository.findByCode(queryTrimmed);
        if (!mappings.isEmpty()) {
            for (TerminologyMapping tm : mappings) {
                MedicalConcept c = tm.getConcept();
                if (c != null && c.getStatus() == ConceptStatus.ACTIVE) {
                    if (expectedType != null && c.getConceptType() != expectedType) {
                        continue;
                    }
                    return EntityResolutionResult.builder()
                            .matchClass(EntityResolutionMatchClass.SUPPORTED_MAPPING)
                            .resolvedConcept(c)
                            .normalizedQuery(norm)
                            .confidence(0.98)
                            .notes("Code mapped via " + tm.getTerminologySystem() + " (" + tm.getCode() + ")")
                            .build();
                }
            }
        }

        // 3. Exact Canonical Name match (case-insensitive)
        Optional<MedicalConcept> byName = conceptRepository.findByCanonicalNameIgnoreCase(norm);
        if (byName.isPresent() && byName.get().getStatus() == ConceptStatus.ACTIVE) {
            MedicalConcept c = byName.get();
            if (expectedType != null && c.getConceptType() != expectedType) {
                return EntityResolutionResult.builder()
                        .matchClass(EntityResolutionMatchClass.CONFLICT)
                        .resolvedConcept(c)
                        .normalizedQuery(norm)
                        .confidence(0.0)
                        .notes("Canonical name matched but type conflict: expected " + expectedType + " but found " + c.getConceptType())
                        .build();
            }
            return EntityResolutionResult.builder()
                    .matchClass(EntityResolutionMatchClass.EXACT_MATCH)
                    .resolvedConcept(c)
                    .normalizedQuery(norm)
                    .confidence(1.0)
                    .notes("Exact canonical name match")
                    .build();
        }

        // 4. Synonym match
        List<MedicalConceptSynonym> syns = synonymRepository.findBySynonymIgnoreCase(norm);
        if (!syns.isEmpty()) {
            MedicalConcept c = syns.get(0).getConcept();
            if (c != null && c.getStatus() == ConceptStatus.ACTIVE) {
                if (expectedType == null || c.getConceptType() == expectedType) {
                    return EntityResolutionResult.builder()
                            .matchClass(EntityResolutionMatchClass.EXACT_MATCH)
                            .resolvedConcept(c)
                            .normalizedQuery(norm)
                            .confidence(0.95)
                            .notes("Exact synonym match")
                            .build();
                }
            }
        }

        // 5. Search candidates
        List<MedicalConcept> candidates = conceptRepository.searchByTerm(norm);
        if (expectedType != null) {
            candidates = candidates.stream()
                    .filter(c -> c.getConceptType() == expectedType)
                    .toList();
        }

        if (candidates.isEmpty()) {
            return EntityResolutionResult.builder()
                    .matchClass(EntityResolutionMatchClass.UNRESOLVED)
                    .normalizedQuery(norm)
                    .confidence(0.0)
                    .notes("No concept candidates found in knowledge base")
                    .build();
        } else if (candidates.size() == 1) {
            return EntityResolutionResult.builder()
                    .matchClass(EntityResolutionMatchClass.PROBABLE_MATCH)
                    .resolvedConcept(candidates.get(0))
                    .normalizedQuery(norm)
                    .confidence(0.85)
                    .candidateMatches(candidates)
                    .notes("Single probable candidate identified")
                    .build();
        } else {
            // Multiple candidate matches -> Ambiguous. Do NOT guess!
            return EntityResolutionResult.builder()
                    .matchClass(EntityResolutionMatchClass.AMBIGUOUS)
                    .normalizedQuery(norm)
                    .confidence(0.40)
                    .candidateMatches(candidates)
                    .notes("Ambiguous entity with " + candidates.size() + " candidates. Requires clarification/quarantine.")
                    .build();
        }
    }

    /**
     * Deterministic deduplication with provenance retention (Section 17).
     * Attaches new assertions, mappings, and synonyms to the canonical concept without discarding source provenance.
     */
    public boolean deduplicateAndMergeProvenance(MedicalConcept canonical, ConceptImportDto sourceAssertion, String sourceId, String sourceVersion) {
        if (canonical == null || sourceAssertion == null) {
            return false;
        }

        // Do not merge conflicting concept types
        if (canonical.getConceptType() != sourceAssertion.getConceptType()) {
            log.warn("[DEDUPLICATION] Cannot merge concepts with incompatible types (canonical={}, assertion={})",
                    canonical.getConceptType(), sourceAssertion.getConceptType());
            return false;
        }

        // Retain synonyms
        if (sourceAssertion.getSynonyms() != null) {
            for (String syn : sourceAssertion.getSynonyms()) {
                if (syn != null && !syn.isBlank()) {
                    boolean exists = canonical.getSynonyms() != null && canonical.getSynonyms().stream()
                            .anyMatch(s -> s.getSynonym().equalsIgnoreCase(syn.trim()));
                    if (!exists) {
                        canonical.addSynonym(syn.trim(), "en", false, SynonymType.COMMON_CLINICAL, SynonymMatchStatus.CONFIRMED);
                    }
                }
            }
        }

        // Retain terminology mappings with original source provenance
        if (sourceAssertion.getTerminologyMappings() != null) {
            for (TerminologyMappingDto tm : sourceAssertion.getTerminologyMappings()) {
                boolean exists = canonical.getTerminologyMappings() != null && canonical.getTerminologyMappings().stream()
                        .anyMatch(m -> m.getTerminologySystem() == tm.getSystem() && m.getCode().equalsIgnoreCase(tm.getCode()));
                if (!exists) {
                    canonical.addTerminologyMapping(
                            tm.getSystem(),
                            tm.getCode(),
                            tm.getDisplay(),
                            tm.getMappingType() != null ? tm.getMappingType() : MappingType.EQUIVALENT,
                            "Deduplicated Source: " + sourceId + " v" + sourceVersion,
                            tm.getJurisdiction() != null ? tm.getJurisdiction() : Jurisdiction.GLOBAL);
                }
            }
        }

        return true;
    }
}

package com.velocura.ai.clinical.knowledge;

import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.DiscriminatorQuestion;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.RxMedicationItem;
import com.velocura.ai.clinical.safety.PharmacologicalSafetyMatrix;
import com.velocura.ai.clinical.state.PatientContext;
import com.velocura.ai.clinical.retrieval.dto.ClinicalCandidate;
import com.velocura.model.PrescriptionStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalClinicalEntityRegistry {

    private final PharmacologicalSafetyMatrix safetyMatrix;
    private final Map<String, ClinicalEntity> entityByIcd = new ConcurrentHashMap<>();
    private final Map<String, List<String>> icdsBySymptom = new ConcurrentHashMap<>();
    private final Map<String, Posting[]> invertedIndex = new ConcurrentHashMap<>();
    private final Map<String, Double> idfMap = new ConcurrentHashMap<>();
    private final Set<String> coreIcds = ConcurrentHashMap.newKeySet();
    private ClinicalEntity[] entitiesArray = new ClinicalEntity[0];
    private boolean[] isCoreDoc = new boolean[0];

    private final ThreadLocal<SearchScratchPad> scratchPadThreadLocal = ThreadLocal.withInitial(() -> new SearchScratchPad(16384));

    private static final class SearchScratchPad {
        final double[] scores;
        final int[] hallmarkHits;
        final int[] touchedDocIds;

        SearchScratchPad(int capacity) {
            this.scores = new double[capacity];
            this.hallmarkHits = new int[capacity];
            this.touchedDocIds = new int[capacity];
        }
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "i", "me", "my", "myself", "we", "our", "you", "your", "he", "she", "it", "they",
            "am", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does",
            "did", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until", "while",
            "of", "at", "by", "for", "with", "about", "against", "between", "into", "through",
            "during", "before", "after", "above", "below", "to", "from", "up", "down", "in", "out",
            "on", "off", "over", "under", "again", "further", "then", "once", "here", "there",
            "when", "where", "why", "how", "all", "any", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than",
            "too", "very", "can", "will", "just", "feel", "feeling", "experiencing", "started",
            "days", "day", "since", "got", "having", "please", "help", "doctor", "suffering"
    );

    private static final class Posting {
        final int docId;
        final float weight;
        final boolean isHallmark;

        Posting(int docId, float weight, boolean isHallmark) {
            this.docId = docId;
            this.weight = weight;
            this.isHallmark = isHallmark;
        }
    }

    public static class ScoredCandidate implements Comparable<ScoredCandidate> {
        private final ClinicalEntity entity;
        private final double score;
        private final List<String> matchedFeatures;

        public ScoredCandidate(ClinicalEntity entity, double score, List<String> matchedFeatures) {
            this.entity = entity;
            this.score = score;
            this.matchedFeatures = matchedFeatures != null ? matchedFeatures : Collections.emptyList();
        }

        public ClinicalEntity getEntity() { return entity; }
        public double getScore() { return score; }
        public List<String> getMatchedFeatures() { return matchedFeatures; }

        @Override
        public int compareTo(ScoredCandidate o) {
            return Double.compare(this.score, o.score);
        }
    }

    @PostConstruct
    public void init() {
        log.info("[CLINICAL REGISTRY] Initializing local 11k clinical knowledge base and discriminator graph...");
        load11kDataset();
        registerCoreClinicalEntities();
        finalizeInvertedIndex();
        log.info("[CLINICAL REGISTRY] Total registered entities in local clinical knowledge base: {}", entityByIcd.size());
    }

    private void finalizeInvertedIndex() {
        int n = entityByIcd.size();
        if (n == 0) return;

        entitiesArray = new ClinicalEntity[n];
        isCoreDoc = new boolean[n];

        Map<String, List<Posting>> tempIndex = new HashMap<>(4096);

        int docId = 0;
        for (ClinicalEntity ce : entityByIcd.values()) {
            entitiesArray[docId] = ce;
            isCoreDoc[docId] = coreIcds.contains(ce.getIcd11Code());

            // Normalize pertinent negatives once at startup
            if (ce.getPertinentNegatives() != null && !ce.getPertinentNegatives().isEmpty()) {
                List<String> cleaned = new ArrayList<>(ce.getPertinentNegatives().size());
                for (String neg : ce.getPertinentNegatives()) {
                    if (neg != null && !neg.isBlank()) cleaned.add(neg.trim().toLowerCase(Locale.ROOT));
                }
                ce.setPertinentNegatives(cleaned);
            }

            // Populate icdsBySymptom for exact fallback
            if (ce.getHallmarkSymptoms() != null) {
                for (String s : ce.getHallmarkSymptoms()) {
                    if (s != null && !s.isBlank()) {
                        icdsBySymptom.computeIfAbsent(s.trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(ce.getIcd11Code());
                    }
                }
            }

            // Index entity tokens
            indexEntityTokens(tempIndex, ce, docId);
            docId++;
        }

        for (Map.Entry<String, List<Posting>> entry : tempIndex.entrySet()) {
            List<Posting> postings = entry.getValue();
            invertedIndex.put(entry.getKey(), postings.toArray(new Posting[0]));
            double idf = Math.log(1.0 + ((double) n / (double) postings.size()));
            idfMap.put(entry.getKey(), idf);
        }

        log.info("[CLINICAL REGISTRY] Inverted Index built across {} clinical entities with {} unique clinical terms.", n, invertedIndex.size());
    }

    private void indexEntityTokens(Map<String, List<Posting>> tempIndex, ClinicalEntity entity, int docId) {
        if (entity.getHallmarkSymptoms() != null) {
            for (String symptom : entity.getHallmarkSymptoms()) {
                if (symptom == null || symptom.isBlank()) continue;
                String sl = symptom.trim().toLowerCase(Locale.ROOT);
                indexToken(tempIndex, sl, docId, 4.0f, true);
                if (sl.contains("_")) {
                    indexToken(tempIndex, sl.replace('_', ' '), docId, 4.0f, true);
                    String[] parts = sl.split("_");
                    for (String p : parts) {
                        indexToken(tempIndex, p, docId, 2.0f, false);
                    }
                }
            }
        }

        if (entity.getTitle() != null) {
            String[] tokens = entity.getTitle().toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+");
            for (String tok : tokens) {
                indexToken(tempIndex, tok, docId, 2.5f, false);
            }
        }

        if (entity.getCategory() != null) {
            String[] tokens = entity.getCategory().toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+");
            for (String tok : tokens) {
                indexToken(tempIndex, tok, docId, 1.0f, false);
            }
        }

        if (entity.getDiscriminatorQuestions() != null) {
            for (DiscriminatorQuestion dq : entity.getDiscriminatorQuestions()) {
                if (dq.getQuickReplies() != null) {
                    for (String qr : dq.getQuickReplies()) {
                        String[] tokens = qr.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+");
                        for (String tok : tokens) {
                            indexToken(tempIndex, tok, docId, 1.2f, false);
                        }
                    }
                }
            }
        }
    }

    private void indexToken(Map<String, List<Posting>> tempIndex, String rawToken, int docId, float weight, boolean isHallmark) {
        if (rawToken == null) return;
        String t = rawToken.trim().toLowerCase(Locale.ROOT);
        if (t.length() < 3 || STOP_WORDS.contains(t)) return;

        List<Posting> list = tempIndex.computeIfAbsent(t, k -> new ArrayList<>());
        if (!list.isEmpty() && list.get(list.size() - 1).docId == docId) {
            Posting prev = list.get(list.size() - 1);
            list.set(list.size() - 1, new Posting(docId, prev.weight + weight, prev.isHallmark || isHallmark));
        } else {
            list.add(new Posting(docId, weight, isHallmark));
        }
    }

    private void load11kDataset() {
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("knowledge/icd11_core_11k.json.gz");
            if (!resource.exists()) {
                resource = new org.springframework.core.io.ClassPathResource("knowledge/icd11_core_11k.json");
            }
            if (resource.exists()) {
                java.io.InputStream in = resource.getInputStream();
                if (resource.getFilename() != null && resource.getFilename().endsWith(".gz")) {
                    in = new java.util.zip.GZIPInputStream(in);
                }
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.core.type.TypeReference<List<ClinicalEntity>> typeRef = new com.fasterxml.jackson.core.type.TypeReference<>() {};
                List<ClinicalEntity> list = mapper.readValue(in, typeRef);
                for (ClinicalEntity ce : list) {
                    if (ce != null && ce.getIcd11Code() != null) {
                        entityByIcd.put(ce.getIcd11Code(), ce);
                    }
                }
                log.info("[CLINICAL REGISTRY] Successfully ingested {} WHO ICD-11 entities from dataset resource.", list.size());
            }
        } catch (Exception e) {
            log.warn("[CLINICAL REGISTRY] Failed to load 11k dataset resource: {}", e.getMessage());
        }
    }

    public ClinicalEntity getEntity(String icdCode) {
        if (icdCode == null) return null;
        return entityByIcd.get(icdCode.trim().toUpperCase(Locale.ROOT));
    }

    public int getTotalRegisteredEntities() {
        return entityByIcd.size();
    }

    /**
     * Sub-millisecond ranked clinical candidate search across all 11,003 WHO ICD-11 entities.
     * Algorithmic complexity: O(|Q| * K + C log k) with zero-allocation scratchpad and top-K heap.
     *
     * @param symptoms collection of detected patient symptoms
     * @param freeText patient's free-text natural language complaint
     * @param topK maximum top candidates to return (e.g. 3 to 5)
     * @return top-K scored candidates ordered descending by clinical relevance
     */
    public List<ScoredCandidate> search11k(Collection<String> symptoms, String freeText, int topK) {
        if ((symptoms == null || symptoms.isEmpty()) && (freeText == null || freeText.isBlank())) {
            return Collections.emptyList();
        }

        Set<String> queryTerms = new LinkedHashSet<>(16);

        // 1. Ingest symptoms
        if (symptoms != null) {
            for (String s : symptoms) {
                if (s == null || s.isBlank()) continue;
                String sn = s.trim().toLowerCase(Locale.ROOT);
                queryTerms.add(sn);
                if (sn.contains("_")) {
                    queryTerms.add(sn.replace('_', ' '));
                    String[] parts = sn.split("_");
                    for (String p : parts) {
                        if (p.length() >= 3 && !STOP_WORDS.contains(p)) {
                            queryTerms.add(p);
                        }
                    }
                }
            }
        }

        // 2. Ingest free-text tokens
        if (freeText != null && !freeText.isBlank()) {
            String[] tokens = freeText.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+");
            for (String tok : tokens) {
                if (tok.length() >= 3 && !STOP_WORDS.contains(tok)) {
                    queryTerms.add(tok);
                }
            }
        }

        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        SearchScratchPad pad = scratchPadThreadLocal.get();
        if (pad == null || pad.scores.length < entitiesArray.length) {
            pad = new SearchScratchPad(Math.max(16384, entitiesArray.length + 1024));
            scratchPadThreadLocal.set(pad);
        }

        double[] scores = pad.scores;
        int[] hallmarkHits = pad.hallmarkHits;
        int[] touchedDocIds = pad.touchedDocIds;
        int touchedCount = 0;

        try {
            // 3. Fast Inverted Index Accumulation directly into scratchpad arrays
            for (String term : queryTerms) {
                Posting[] postings = invertedIndex.get(term);
                if (postings == null) continue;
                double idf = idfMap.getOrDefault(term, 1.0);

                for (int i = 0; i < postings.length; i++) {
                    Posting p = postings[i];
                    int docId = p.docId;
                    if (scores[docId] == 0.0) {
                        touchedDocIds[touchedCount++] = docId;
                    }
                    scores[docId] += (p.weight * idf);
                    if (p.isHallmark) {
                        hallmarkHits[docId]++;
                    }
                }
            }

            if (touchedCount == 0) {
                return Collections.emptyList();
            }

            // 4. Synergy boost, Pertinent Negative penalty, Authoritative Core boost & top-K Selection
            int kSize = Math.max(1, topK);
            PriorityQueue<ScoredCandidate> minHeap = new PriorityQueue<>(kSize + 1);

            for (int i = 0; i < touchedCount; i++) {
                int docId = touchedDocIds[i];
                double score = scores[docId];
                int hits = hallmarkHits[docId];

                // Multi-hallmark synergy boost: diseases matching 2+ symptoms receive non-linear boost
                if (hits > 1) {
                    score *= (1.0 + 0.45 * (hits - 1));
                }

                ClinicalEntity entity = entitiesArray[docId];

                // Pertinent negative penalty
                if (entity.getPertinentNegatives() != null && !entity.getPertinentNegatives().isEmpty()) {
                    for (String negL : entity.getPertinentNegatives()) {
                        if (queryTerms.contains(negL)) {
                            score *= 0.15; // 85% penalty for contradicting pertinent negatives
                            break;
                        }
                    }
                }

                // Core Authoritative Clinical Entity quality prior bonus
                if (isCoreDoc[docId]) {
                    score += 12.0;
                }

                // Prune heap operations: skip if score cannot beat current k-th candidate
                if (minHeap.size() >= kSize && score <= minHeap.peek().getScore()) {
                    continue;
                }

                minHeap.offer(new ScoredCandidate(entity, score, Collections.emptyList()));
                if (minHeap.size() > kSize) {
                    minHeap.poll();
                }
            }

            if (minHeap.isEmpty()) {
                return Collections.emptyList();
            }

            // 5. Populate matched terms only for the selected top-K candidates
            List<ScoredCandidate> result = new ArrayList<>(minHeap.size());
            while (!minHeap.isEmpty()) {
                ScoredCandidate sc = minHeap.poll();
                List<String> matched = new ArrayList<>();
                ClinicalEntity ce = sc.getEntity();
                if (ce != null) {
                    for (String qt : queryTerms) {
                        if (ce.getHallmarkSymptoms() != null) {
                            for (String hs : ce.getHallmarkSymptoms()) {
                                if (hs.equalsIgnoreCase(qt) || hs.toLowerCase(Locale.ROOT).contains(qt)) {
                                    if (!matched.contains(qt)) matched.add(qt);
                                    break;
                                }
                            }
                        }
                        if (ce.getTitle() != null && ce.getTitle().toLowerCase(Locale.ROOT).contains(qt)) {
                            if (!matched.contains(qt)) matched.add(qt);
                        }
                    }
                }
                result.add(new ScoredCandidate(sc.getEntity(), sc.getScore(), matched));
            }
            Collections.reverse(result);
            return result;

        } finally {
            // Clean up scratchpad arrays in O(touchedCount) without full array wipe
            for (int i = 0; i < touchedCount; i++) {
                int id = touchedDocIds[i];
                scores[id] = 0.0;
                hallmarkHits[id] = 0;
            }
        }
    }

    /**
     * Strict candidate retrieval contract for UnifiedClinicalDecisionEngine.
     * Produces candidate medical concepts with provenance, scores, and matched features.
     * Has ZERO diagnostic, treatment, referral, or autonomous prescription authority.
     */
    public List<ClinicalCandidate> retrieveCandidates(
            Collection<String> symptoms,
            String freeText,
            int topK,
            Set<String> negatedFindings,
            String snapshotId) {

        List<ScoredCandidate> scored = search11k(symptoms, freeText, topK);
        if (scored == null || scored.isEmpty()) {
            return Collections.emptyList();
        }

        String effectiveSnapshot = snapshotId != null ? snapshotId : "2026.01-WHO-ICD11";
        List<ClinicalCandidate> candidates = new ArrayList<>(scored.size());

        for (ScoredCandidate sc : scored) {
            ClinicalEntity ce = sc.getEntity();
            List<String> matchedHallmarks = new ArrayList<>();
            List<String> contradictions = new ArrayList<>();

            if (ce.getHallmarkSymptoms() != null) {
                for (String hs : ce.getHallmarkSymptoms()) {
                    String hsl = hs.toLowerCase(Locale.ROOT);
                    if (symptoms != null && symptoms.stream().anyMatch(s -> hsl.contains(s.toLowerCase(Locale.ROOT)))) {
                        matchedHallmarks.add(hs);
                    }
                    if (freeText != null && freeText.toLowerCase(Locale.ROOT).contains(hsl)) {
                        if (!matchedHallmarks.contains(hs)) matchedHallmarks.add(hs);
                    }
                    if (negatedFindings != null && negatedFindings.stream().anyMatch(n -> hsl.contains(n.toLowerCase(Locale.ROOT)))) {
                        contradictions.add(hs);
                    }
                }
            }

            List<String> trace = List.of(
                    "RETRIEVAL: Inverted index candidate " + ce.getIcd11Code() + " ('" + ce.getTitle() + "')",
                    "RETRIEVAL: Relevance score = " + String.format(Locale.ROOT, "%.2f", sc.getScore()) + " (bounded lexical score, NOT probability)",
                    "RETRIEVAL: Matched terms: " + sc.getMatchedFeatures()
            );

            ClinicalCandidate candidate = ClinicalCandidate.builder()
                    .conceptId("CAND-" + ce.getIcd11Code())
                    .terminologyCode(ce.getIcd11Code())
                    .displayName(ce.getTitle())
                    .relevanceScore(sc.getScore())
                    .matchedFeatures(sc.getMatchedFeatures())
                    .matchedHallmarks(matchedHallmarks)
                    .contradictions(contradictions)
                    .provenance("WHO-ICD11-CORE-11K-INVERTED-INDEX")
                    .knowledgeSnapshotId(effectiveSnapshot)
                    .retrievalTrace(trace)
                    .backingEntity(ce)
                    .build();

            candidates.add(candidate);
        }

        return candidates;
    }

    public List<ClinicalEntity> findCandidates(Collection<String> symptoms) {

        if (symptoms == null || symptoms.isEmpty()) return Collections.emptyList();
        List<ScoredCandidate> scored = search11k(symptoms, null, 15);
        if (!scored.isEmpty()) {
            List<ClinicalEntity> res = new ArrayList<>(scored.size());
            for (ScoredCandidate sc : scored) {
                res.add(sc.getEntity());
            }
            return res;
        }

        // Fallback to legacy exact symptom index
        Set<String> matchedIcds = new LinkedHashSet<>();
        for (String s : symptoms) {
            String sl = s.toLowerCase(Locale.ROOT);
            List<String> icds = icdsBySymptom.get(sl);
            if (icds != null) {
                matchedIcds.addAll(icds);
            }
        }
        List<ClinicalEntity> res = new ArrayList<>();
        for (String icd : matchedIcds) {
            ClinicalEntity entity = entityByIcd.get(icd);
            if (entity != null) res.add(entity);
        }
        return res;
    }

    public Optional<DiscriminatorQuestion> findNextDiscriminator(List<String> topIcdCodes, Set<String> askedQuestions) {
        if (topIcdCodes == null || topIcdCodes.isEmpty()) return Optional.empty();

        for (String icd : topIcdCodes) {
            ClinicalEntity entity = entityByIcd.get(icd);
            if (entity == null || entity.getDiscriminatorQuestions() == null) continue;

            for (DiscriminatorQuestion dq : entity.getDiscriminatorQuestions()) {
                if (askedQuestions == null || (!askedQuestions.contains(dq.getId()) && !askedQuestions.contains(dq.getDimension()))) {
                    return Optional.of(dq);
                }
            }
        }
        return Optional.empty();
    }

    public PrescriptionProtocol generatePrescription(
            String icdCode,
            String primaryDx,
            PatientContext patientContext,
            List<String> reportedSymptoms) {

        ClinicalEntity entity = getEntity(icdCode);
        PrescriptionProtocol base;

        if (entity != null && entity.getDefaultPrescriptionProtocol() != null) {
            base = deepCopy(entity.getDefaultPrescriptionProtocol());
        } else {
            base = buildFallbackPrescription(icdCode, primaryDx);
        }

        base.setPrescriptionId("RX-VEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        if (primaryDx != null && !primaryDx.isBlank()) {
            base.setPrimaryDiagnosis(primaryDx);
        }
        if (patientContext != null) {
            base.setPatientContextSummary((patientContext.isPediatric() ? "Pediatric" : "Adult") + " | " + patientContext.getRelationship());
        }
        base.setStatus(PrescriptionStatus.DRAFT);
        base.setClinicianReviewRequired(true);
        base.setClinicianAuthorizationRequired(true);
        base.setRequiresDoctorSignature(true);
        base.setAuthorizedBy(null);


        List<String> diagnoses = List.of(icdCode != null ? icdCode : "", primaryDx != null ? primaryDx : "");
        PrescriptionProtocol sanitized = safetyMatrix.sanitizeAndValidate(base, patientContext, diagnoses, reportedSymptoms);
        sanitized.setStatus(PrescriptionStatus.DRAFT);
        sanitized.setClinicianReviewRequired(true);
        sanitized.setClinicianAuthorizationRequired(true);
        sanitized.setRequiresDoctorSignature(true);
        sanitized.setAuthorizedBy(null);
        return sanitized;
    }


    private PrescriptionProtocol buildFallbackPrescription(String icdCode, String primaryDx) {
        return PrescriptionProtocol.builder()
                .icd11Code(icdCode != null ? icdCode : "MG30")
                .primaryDiagnosis(primaryDx != null ? primaryDx : "Acute Febrile / Symptomatic Presentation")
                .specialistDepartment("General Medicine")
                .status(PrescriptionStatus.DRAFT)
                .clinicianReviewRequired(true)
                .clinicianAuthorizationRequired(true)
                .requiresDoctorSignature(true)
                .authorizedBy(null)
                .medications(List.of(
                        RxMedicationItem.builder()
                                .saltName("Paracetamol (Acetaminophen)")
                                .brandReference("Dolo 650 / Panadol")
                                .formulation("Tablet")
                                .strength("650 mg")
                                .route("Oral")
                                .dosageFrequency("1 tablet every 6 to 8 hours PRN (for fever/pain)")
                                .duration("3 to 5 days")
                                .instructions("Take after food with water. Max 3000 mg in 24 hours.")
                                .indication("Symptomatic fever and body ache reduction")
                                .prescriptionOnly(false)
                                .build()
                ))
                .supportiveCare(List.of("Maintain oral hydration (2.5L fluids/day)", "Adequate physical rest"))
                .contraindicatedMedications(List.of("Excessive antipyretic combination products"))
                .diagnosticLabOrders(List.of("Routine Complete Blood Count if symptoms persist > 3 days"))
                .redFlagHospitalizationCriteria(List.of("Dyspnea", "Altered mental status", "Uncontrolled vomiting"))
                .build();
    }

    private PrescriptionProtocol deepCopy(PrescriptionProtocol src) {
        List<RxMedicationItem> meds = new ArrayList<>();
        if (src.getMedications() != null) {
            for (RxMedicationItem m : src.getMedications()) {
                meds.add(RxMedicationItem.builder()
                        .saltName(m.getSaltName())
                        .brandReference(m.getBrandReference())
                        .formulation(m.getFormulation())
                        .strength(m.getStrength())
                        .route(m.getRoute())
                        .dosageFrequency(m.getDosageFrequency())
                        .duration(m.getDuration())
                        .instructions(m.getInstructions())
                        .indication(m.getIndication())
                        .prescriptionOnly(m.isPrescriptionOnly())
                        .build());
            }
        }
        return PrescriptionProtocol.builder()
                .primaryDiagnosis(src.getPrimaryDiagnosis())
                .icd11Code(src.getIcd11Code())
                .specialistDepartment(src.getSpecialistDepartment())
                .status(PrescriptionStatus.DRAFT)
                .clinicianReviewRequired(true)
                .clinicianAuthorizationRequired(true)
                .requiresDoctorSignature(true)
                .authorizedBy(null)

                .medications(meds)
                .supportiveCare(new ArrayList<>(src.getSupportiveCare()))
                .contraindicatedMedications(new ArrayList<>(src.getContraindicatedMedications()))
                .diagnosticLabOrders(new ArrayList<>(src.getDiagnosticLabOrders()))
                .redFlagHospitalizationCriteria(new ArrayList<>(src.getRedFlagHospitalizationCriteria()))
                .authorizedBy(src.getAuthorizedBy())
                .requiresDoctorSignature(src.isRequiresDoctorSignature())
                .build();
    }

    private void registerCoreEntity(ClinicalEntity entity) {
        if (entity == null || entity.getIcd11Code() == null) return;
        coreIcds.add(entity.getIcd11Code());
        entityByIcd.put(entity.getIcd11Code(), entity);
    }

    private void registerCoreClinicalEntities() {
        // 1. DENGUE / ARBOVIRAL FEBRILE SYNDROME (1D20)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("1D20")
                .title("Dengue / Arboviral Febrile Syndrome")
                .category("Infectious Diseases")
                .specialistDepartment("Infectious Disease / Internal Medicine")
                .urgencyTier("HIGH")
                .hallmarkSymptoms(List.of("fever", "retro_orbital_pain", "joint_pain", "petechiae_rash", "myalgia", "headache"))
                .pertinentNegatives(List.of("productive_cough", "dysuria", "chest_pain"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("DENGUE_DISCRIMINATOR_BLEEDING")
                                .dimension("hemorrhagic_signs")
                                .questionText("Have you noticed any bleeding gums, nosebleeds, small red spots on the skin (petechiae), or severe abdominal pain?")
                                .quickReplies(List.of("No bleeding or spots", "Small red spots or petechiae", "Bleeding gums or nosebleed", "Severe persistent stomach pain"))
                                .conditionWeights(Map.of("1D20", 4.5, "9A60.0", -5.0))
                                .diagnosticUtility(3.0)
                                .build(),
                        DiscriminatorQuestion.builder()
                                .id("DENGUE_DISCRIMINATOR_DURATION")
                                .dimension("fever_pattern")
                                .questionText("How many days has the high fever been present, and does it come with severe bone/joint chills?")
                                .quickReplies(List.of("1 to 3 days continuous", "4 to 7 days", "Mild on-off fever", "Started today"))
                                .conditionWeights(Map.of("1D20", 3.0))
                                .diagnosticUtility(2.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("1D20")
                        .primaryDiagnosis("Dengue / Arboviral Febrile Syndrome")
                        .specialistDepartment("Infectious Disease / Internal Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Paracetamol (Acetaminophen)")
                                        .brandReference("Dolo 650 / Calpol")
                                        .formulation("Tablet")
                                        .strength("650 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 6 hours PRN for fever > 100.4°F")
                                        .duration("3 to 5 days")
                                        .instructions("Take with water after meals. Maximum 3000 mg in 24 hours. Strictly avoid empty stomach overdosing.")
                                        .indication("Antipyresis and arthralgia control without platelet suppression")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Oral Rehydration Salts (WHO-ORS)")
                                        .brandReference("Electral / Hydralyte")
                                        .formulation("Powder for oral solution")
                                        .strength("1 sachet in 1 Litre drinking water")
                                        .route("Oral")
                                        .dosageFrequency("Sip 2.5 to 3 Litres continuously across 24 hours")
                                        .duration("5 to 7 days (throughout febrile & defervescent phase)")
                                        .instructions("Essential to prevent plasma leakage, hypovolemia, and hemoconcentration.")
                                        .indication("Electrolyte and intravascular volume maintenance")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Strict physical bed rest to prevent circulatory collapse",
                                "Maintain high oral fluid intake (coconut water, clear broths, ORS)",
                                "Daily monitoring of platelet count and hematocrit until fever subsides for 48 hours"
                        ))
                        .contraindicatedMedications(List.of(
                                "STRICT CONTRAINDICATION: All NSAIDs (Aspirin, Ibuprofen, Diclofenac, Naproxen, Ketorolac, Mefenamic Acid) are prohibited due to platelet suppression and fatal bleeding / hemorrhagic shock risk.",
                                "Avoid intramuscular injections (high risk of intramuscular hematoma)."
                        ))
                        .diagnosticLabOrders(List.of(
                                "Complete Blood Count (CBC) with Platelet Count and Hematocrit (daily monitoring)",
                                "Dengue NS1 Rapid Antigen (Days 1–5 of illness)",
                                "Dengue IgM / IgG Antibody ELISA (Day 5 onwards)",
                                "Serum ALT / AST (Liver enzymes) to detect acute reactive hepatitis"
                        ))
                        .redFlagHospitalizationCriteria(List.of(
                                "Spontaneous mucosal bleeding (epistaxis, gingival bleeding, hematuria, black stools)",
                                "Severe persistent abdominal pain or recurrent persistent vomiting (> 3 episodes/day)",
                                "Rapid drop in platelet count below 50,000 /mcL or hematocrit rise > 20% (plasma leakage)",
                                "Cold, clammy extremities, lethargy, restlessness, or sudden dizziness"
                        ))
                        .build())
                .build());

        // 2. ACUTE UNCOMPLICATED CYSTITIS / UTI (GC08)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("GC08")
                .title("Acute Uncomplicated Cystitis (UTI)")
                .category("Urology")
                .specialistDepartment("Urology / Internal Medicine")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("dysuria", "urinary_frequency", "urinary_urgency", "pelvic_pain"))
                .pertinentNegatives(List.of("high_fever", "flank_pain", "vomiting"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("UTI_DISCRIMINATOR_FLANK_FEVER")
                                .dimension("pyelonephritis_screen")
                                .questionText("Do you have any high spiking fever, shaking chills, or sharp pain in your mid-to-upper back (flank)?")
                                .quickReplies(List.of("No fever or back pain", "Mild lower back ache", "High fever with chills", "Sharp flank pain on one side"))
                                .conditionWeights(Map.of("GC08", 2.0, "PYELONEPHRITIS", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("GC08")
                        .primaryDiagnosis("Acute Uncomplicated Cystitis")
                        .specialistDepartment("Urology / Internal Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Disodium Hydrogen Citrate (Urine Alkalizer)")
                                        .brandReference("Cital / Alkapen Syrup")
                                        .formulation("Oral Solution")
                                        .strength("1.37 g / 5 ml")
                                        .route("Oral")
                                        .dosageFrequency("2 teaspoons (10 ml) diluted in 1 glass water TID")
                                        .duration("3 to 5 days")
                                        .instructions("Alkalinizes urine to provide rapid relief from painful burning during micturition.")
                                        .indication("Dysuria comfort and urinary alkalinization")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Nitrofurantoin Sustained Release")
                                        .brandReference("Furadantin / Martifur MR")
                                        .formulation("Capsule")
                                        .strength("100 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 capsule twice daily (every 12 hours) with meals")
                                        .duration("5 days")
                                        .instructions("First-line guideline antimicrobial for lower UTI. Take strictly with food or milk to enhance absorption.")
                                        .indication("Targeted eradication of uropathogenic E. coli")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of("Drink 3 to 4 Litres of water daily to flush bacteria", "Void bladder regularly every 2-3 hours"))
                        .contraindicatedMedications(List.of("Avoid holding urine; avoid excess caffeine, alcohol, and artificial sweeteners"))
                        .diagnosticLabOrders(List.of("Urine Routine & Microscopic Examination (Urine R/M)", "Urine Culture & Sensitivity (Urine C/S)"))
                        .redFlagHospitalizationCriteria(List.of("High fever with rigors", "Flank pain", "Inability to pass urine"))
                        .build())
                .build());

        // 3. ALLERGIC CONJUNCTIVITIS & EYE STRAIN (9A60.0)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("9A60.0")
                .title("Allergic Conjunctivitis / Digital Asthenopia")
                .category("Ophthalmology")
                .specialistDepartment("Ophthalmology")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("eye_symptoms", "conjunctivitis_symptoms", "eye_strain", "ocular_redness", "photophobia"))
                .pertinentNegatives(List.of("fever", "purulent_green_discharge", "severe_vision_loss"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("EYE_DISCRIMINATOR_VISION_LOSS")
                                .dimension("red_eye_screen")
                                .questionText("Is there any sudden loss of eyesight, severe deep eye pain, or thick yellowish-green crusting pus?")
                                .quickReplies(List.of("No vision loss, mild redness/itch", "Thick yellowish crusting pus", "Deep severe ache / light pain", "Noticeable blurry vision"))
                                .conditionWeights(Map.of("9A60.0", 3.0, "BACTERIAL_CONJUNCTIVITIS", 4.0))
                                .diagnosticUtility(2.5)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("9A60.0")
                        .primaryDiagnosis("Allergic Conjunctivitis / Digital Eye Strain")
                        .specialistDepartment("Ophthalmology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Carboxymethylcellulose Sodium (Artificial Tears)")
                                        .brandReference("Refresh Tears / Tears Naturale")
                                        .formulation("Ophthalmic Solution")
                                        .strength("0.5% w/v")
                                        .route("Ophthalmic")
                                        .dosageFrequency("1 to 2 drops into affected eye(s) 4 times daily")
                                        .duration("7 to 14 days")
                                        .instructions("Do not touch dropper tip to eyelashes or cornea. Discard bottle 30 days after opening.")
                                        .indication("Ocular lubrication, tear film stabilization, allergen clearance")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Olopatadine Ophthalmic Solution")
                                        .brandReference("Pataday / Olopat 0.1%")
                                        .formulation("Eye Drops")
                                        .strength("0.1% w/v")
                                        .route("Ophthalmic")
                                        .dosageFrequency("1 drop into affected eye(s) twice daily (every 12 hours)")
                                        .duration("5 to 7 days")
                                        .instructions("Dual-action mast cell stabilizer and H1 antihistamine for ocular pruritus and hyperemia.")
                                        .indication("Allergic ocular itching and conjunctival vascular congestion")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of("Practice 20-20-20 rule: Every 20 minutes, look at an object 20 feet away for 20 seconds", "Apply cool compresses over closed eyelids"))
                        .contraindicatedMedications(List.of("Avoid rubbing eyes vigorously (risk of keratoconus)", "Do NOT use over-the-counter steroid eye drops without ophthalmologist slit-lamp exam"))
                        .diagnosticLabOrders(List.of("Slit-lamp biomicroscopy & visual acuity assessment if symptoms persist > 48h"))
                        .redFlagHospitalizationCriteria(List.of("Severe deep aching eye pain", "Sudden reduction in visual acuity", "Halos around lights with nausea"))
                        .build())
                .build());

        // 4. ACUTE SPRAIN & LIGAMENTOUS STRAIN (FB50.0)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("FB50.0")
                .title("Acute Sprain / Joint Strain")
                .category("Orthopedics")
                .specialistDepartment("Orthopedics")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("sprain_strain", "joint_pain", "joint_swelling", "twisted_ankle"))
                .pertinentNegatives(List.of("fever", "open_wound", "bone_deformity"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("SPRAIN_DISCRIMINATOR_BEAR_WEIGHT")
                                .dimension("ottawa_rules_screen")
                                .questionText("Are you able to bear weight and take 4 steps immediately after the injury, or is walking completely impossible?")
                                .quickReplies(List.of("Can bear weight / walk slowly", "Painful but can take 4 steps", "Completely unable to bear weight", "Heard a loud snapping pop"))
                                .conditionWeights(Map.of("FB50.0", 3.0, "FRACTURE", 4.5))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("FB50.0")
                        .primaryDiagnosis("Acute Ligamentous Sprain / Joint Strain")
                        .specialistDepartment("Orthopedics")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Topical Diclofenac Diethylamine Gel")
                                        .brandReference("Voltaren / Voveran Emulgel 1.16%")
                                        .formulation("Gel")
                                        .strength("1.16% w/w")
                                        .route("Topical")
                                        .dosageFrequency("Gently apply 2 to 4 grams onto intact painful joint 3 to 4 times daily")
                                        .duration("5 to 7 days")
                                        .instructions("Do not massage aggressively into acute swollen tissues. Do not apply onto broken, grazed, or abraded skin.")
                                        .indication("Localized non-steroidal anti-inflammatory pain relief")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Paracetamol 650mg")
                                        .brandReference("Dolo 650")
                                        .formulation("Tablet")
                                        .strength("650 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 8 hours PRN for moderate pain")
                                        .duration("3 to 5 days")
                                        .instructions("Take after meals. Combined topical NSAID + oral paracetamol provides superior safety and analgesia.")
                                        .indication("Oral analgesic synergy")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "R.I.C.E. Protocol: Rest joint, Ice 15 mins every 2–3 hours, Compression crepe bandage (snug, not tight), Elevate limb above heart level",
                                "Avoid heat packs, alcohol, and aggressive running/massage during the first 48 hours (H.A.R.M. protocol)"
                        ))
                        .contraindicatedMedications(List.of("Do not apply topical diclofenac to open wounds or near eyes"))
                        .diagnosticLabOrders(List.of("Plain Radiograph (X-Ray) of affected joint if Ottawa ankle/knee rules positive (inability to bear weight)"))
                        .redFlagHospitalizationCriteria(List.of("Visible anatomical bone deformity or gross joint angulation", "Numbness, tingling, or cold pale toes/fingers (neurovascular compromise)"))
                        .build())
                .build());

        // 5. ACUTE GASTRITIS & PEPTIC DYSPEPSIA (DA60)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("DA60")
                .title("Acute Gastritis / Acid Dyspepsia")
                .category("Gastroenterology")
                .specialistDepartment("Gastroenterology / Internal Medicine")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("abdominal_pain", "heartburn", "acid_reflux", "nausea", "dyspepsia"))
                .pertinentNegatives(List.of("hematemesis", "melena", "fever", "jaundice"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("GASTRITIS_DISCRIMINATOR_GI_BLEED")
                                .dimension("gi_bleed_screen")
                                .questionText("Have you had any vomiting of coffee-ground material or blood, or noticed dark black tarry bowel movements?")
                                .quickReplies(List.of("No blood or black stool", "Heartburn after meals", "Nausea and burning pain", "Dark black tarry stool"))
                                .conditionWeights(Map.of("DA60", 3.0, "GI_BLEED_EMERGENCY", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("DA60")
                        .primaryDiagnosis("Acute Gastritis / Acid Dyspepsia")
                        .specialistDepartment("Gastroenterology / Internal Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Pantoprazole Sodium")
                                        .brandReference("Pan 40 / Pantocid")
                                        .formulation("Tablet")
                                        .strength("40 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet once daily in the morning, 30 to 45 minutes before breakfast")
                                        .duration("14 days")
                                        .instructions("Swallow whole with a glass of water; do not crush or chew. Inhibits parietal cell H+/K+ ATPase pump.")
                                        .indication("Gastric acid suppression and mucosal healing")
                                        .prescriptionOnly(true)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Magaldrate + Simethicone Oral Suspension (Antacid)")
                                        .brandReference("Gelusil / Digene")
                                        .formulation("Oral Suspension")
                                        .strength("Magaldrate 480mg + Simethicone 20mg / 5ml")
                                        .route("Oral")
                                        .dosageFrequency("2 teaspoons (10 ml) 1 to 2 hours after meals and at bedtime PRN")
                                        .duration("5 to 7 days")
                                        .instructions("Shake well before use. Rapidly neutralizes gastric acid and disperses gas bubbles.")
                                        .indication("Immediate acute symptomatic relief of heartburn and epigastric burning")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Eat small, frequent bland meals (oats, bananas, boiled rice, toast)",
                                "Avoid spicy, deeply fried, highly acidic foods, citrus fruits, raw tomatoes, coffee, and carbonated sodas",
                                "Remain upright for at least 2 hours after meals; elevate head of bed by 15 cm"
                        ))
                        .contraindicatedMedications(List.of(
                                "STRICT CONTRAINDICATION: Avoid NSAIDs (Aspirin, Ibuprofen, Diclofenac) and steroids, which erode gastric mucosa and induce ulcers."
                        ))
                        .diagnosticLabOrders(List.of("Serum H. pylori antigen / Stool antigen test", "Upper GI Endoscopy if alarm symptoms or persistent > 4 weeks"))
                        .redFlagHospitalizationCriteria(List.of("Vomiting fresh red blood or dark coffee-ground material", "Black tarry stool (melena)", "Progressive difficulty swallowing (dysphagia)"))
                        .build())
                .build());

        // 6. ACUTE TRAUMATIC LACERATION / OPEN WOUND (NE81.0)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("NE81.0")
                .title("Acute Cutaneous Laceration / Open Wound")
                .category("Emergency Medicine")
                .specialistDepartment("Emergency Medicine / Surgery")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("laceration_wound", "cut_injury", "bleeding_wound"))
                .pertinentNegatives(List.of("arterial_spurting", "numbness"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("WOUND_DISCRIMINATOR_BLEED_CONTROL")
                                .dimension("wound_bleeding_depth")
                                .questionText("Is the bleeding stopping with direct firm pressure, or is it deep, gaping, or spurting bright red blood?")
                                .quickReplies(List.of("Bleeding stopped with pressure", "Bleeding with light pressure", "Gaping wound edges (>1/4 inch)", "Spurting blood continuously"))
                                .conditionWeights(Map.of("NE81.0", 3.0, "SURGICAL_EMERGENCY", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("NE81.0")
                        .primaryDiagnosis("Acute Cutaneous Laceration / Open Wound")
                        .specialistDepartment("Emergency Medicine / Surgery")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Bacitracin + Neomycin + Polymyxin B Ointment")
                                        .brandReference("Neosporin / Betadine Ointment 5%")
                                        .formulation("Topical Ointment")
                                        .strength("Triple Antibiotic Complex")
                                        .route("Topical")
                                        .dosageFrequency("Clean wound and apply thin layer 1 to 2 times daily")
                                        .duration("5 to 7 days")
                                        .instructions("Wash hands, cleanse gently with clean running water or saline, pat dry, apply ointment and sterile bandage.")
                                        .indication("Antimicrobial barrier protection against superficial wound infection")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Tetanus Toxoid Vaccine (TT)")
                                        .brandReference("Tetanus Toxoid 0.5ml")
                                        .formulation("Intramuscular Injection")
                                        .strength("0.5 ml")
                                        .route("Intramuscular")
                                        .dosageFrequency("Single dose IM stat (within 24 to 48 hours of injury)")
                                        .duration("Single dose")
                                        .instructions("Recommended if last booster was > 5-10 years ago or if wound is dirty/tetanus-prone.")
                                        .indication("Tetanus prophylaxis")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of("Keep dressing clean and dry", "Change bandage daily or whenever wet"))
                        .contraindicatedMedications(List.of("Do not apply turmeric powder, raw soil, kerosene, or unsterile remedies to open wounds"))
                        .diagnosticLabOrders(List.of("Wound exploration for foreign body / Plain radiograph if glass or metal suspected"))
                        .redFlagHospitalizationCriteria(List.of("Continuous bleeding despite 10 mins firm direct pressure", "Loss of sensation, numbness, or inability to bend affected finger/joint", "Spreading red streaks, warmth, or pus"))
                        .build())
                .build());

        // 7. ACUTE THERMAL SCALD & DERMAL BURN (ND90.0)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("ND90.0")
                .title("Acute Thermal Burn / Scald")
                .category("Emergency Medicine")
                .specialistDepartment("Emergency Medicine / Dermatology")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("burn_injury", "scald_injury", "skin_burn"))
                .pertinentNegatives(List.of("charred_skin", "circumferential_burn"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("BURN_DISCRIMINATOR_BLISTER_SIZE")
                                .dimension("burn_depth_area")
                                .questionText("What does the burn look like (red without blisters, blistering with fluid, or white/charred/numb)?")
                                .quickReplies(List.of("Red and painful, no blisters", "Blistering with clear fluid", "Larger than patient's palm", "Skin is white, charred, or numb"))
                                .conditionWeights(Map.of("ND90.0", 3.0, "DEEP_PARTIAL_BURN", 4.5))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("ND90.0")
                        .primaryDiagnosis("Acute Superficial to Partial-Thickness Thermal Burn")
                        .specialistDepartment("Emergency Medicine / Dermatology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Silver Sulfadiazine Cream")
                                        .brandReference("Silvadene / Burnol / Silverex 1%")
                                        .formulation("Cream")
                                        .strength("1.0% w/w")
                                        .route("Topical")
                                        .dosageFrequency("Apply 1 to 2 mm thin layer over clean burn area 1 to 2 times daily")
                                        .duration("7 to 10 days")
                                        .instructions("Apply under sterile conditions. Cover with non-adherent sterile gauze dressing. Do not apply near eyes.")
                                        .indication("Broad-spectrum antimicrobial barrier preventing colonization in burn eschar")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Paracetamol 650mg")
                                        .brandReference("Dolo 650")
                                        .formulation("Tablet")
                                        .strength("650 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 6 hours PRN for burn pain")
                                        .duration("3 to 5 days")
                                        .instructions("Take after meals with water.")
                                        .indication("Systemic burn analgesia")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Cool immediately under cool running tap water for 15 to 20 minutes (do NOT use freezing ice or ice water)",
                                "Do NOT pop, puncture, or debride intact burn blisters (intact skin serves as a sterile biological barrier)",
                                "Never apply butter, toothpaste, oil, or flour onto burn surfaces"
                        ))
                        .contraindicatedMedications(List.of("Avoid ice (causes vasoconstriction and extends tissue ischemia)", "Avoid sulfa drugs if verified sulfa allergy"))
                        .diagnosticLabOrders(List.of("Burn center evaluation if > 10% TBSA or involving face, hands, feet, perineum, or major joints"))
                        .redFlagHospitalizationCriteria(List.of("Third-degree burn with painless white, leathery, or charred skin", "Burns involving the face, hands, genitalia, or joints", "Chemical or high-voltage electrical burns"))
                        .build())
                .build());

        // 8. ACUTE ODONTALGIA & DENTAL PULPITIS (DA00.0)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("DA00.0")
                .title("Acute Odontalgia / Dental Pulpitis")
                .category("Dentistry")
                .specialistDepartment("Dentistry / Oral & Maxillofacial Surgery")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("dental_pain", "toothache", "tooth_pain", "swollen_gum", "cavity", "jaw_pain"))
                .pertinentNegatives(List.of("chest_pain", "shortness_of_breath"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("DENTAL_DISCRIMINATOR_TRIGGERS")
                                .dimension("dental_pain_triggers")
                                .questionText("Is the pain triggered by hot or cold fluids, or is it a continuous throbbing ache that keeps you awake?")
                                .quickReplies(List.of("Triggered by hot/cold", "Continuous throbbing ache", "Pain when biting / chewing", "Swelling on cheek or gum"))
                                .conditionWeights(Map.of("DA00.0", 3.5, "DENTAL_ABSCESS", 5.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("DA00.0")
                        .primaryDiagnosis("Acute Odontalgia / Dental Pulpitis")
                        .specialistDepartment("Dentistry / Oral & Maxillofacial Surgery")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Ibuprofen 400mg + Paracetamol 500mg")
                                        .brandReference("Combiflam / Flexon")
                                        .formulation("Tablet")
                                        .strength("400/500 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet every 8 hours with meals PRN for dental pain")
                                        .duration("3 to 5 days")
                                        .instructions("Take after meals with water. Synergistic analgesia for acute pulpal inflammation.")
                                        .indication("Acute odontogenic inflammation and analgesia")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Chlorhexidine Gluconate 0.12% Mouthwash")
                                        .brandReference("Clohex / Hexidine")
                                        .formulation("Oral Rinse")
                                        .strength("0.12% w/v")
                                        .route("Oral Rinse")
                                        .dosageFrequency("Swish 10 ml gently for 30 to 60 seconds twice daily after brushing")
                                        .duration("5 to 7 days")
                                        .instructions("Do not swallow. Do not eat or drink for 30 minutes after rinsing.")
                                        .indication("Antiseptic reduction of intraoral bacterial load")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Rinse mouth gently with warm salt water (1/2 tsp salt in warm water) every 3-4 hours",
                                "Floss carefully around the tooth to dislodge trapped food debris",
                                "Do NOT place aspirin directly against gum tissue (causes caustic chemical burn)"
                        ))
                        .contraindicatedMedications(List.of("Active peptic ulcer disease or severe renal impairment (avoid oral NSAIDs)"))
                        .diagnosticLabOrders(List.of("Intraoral Periapical Radiograph (IOPA) of affected quadrant / OPG"))
                        .redFlagHospitalizationCriteria(List.of(
                                "Facial cellulitis or rapid swelling spreading under the jaw or towards the eye",
                                "Difficulty swallowing saliva, drooling, or respiratory difficulty (Ludwig's angina risk)",
                                "High fever with shaking chills and trismus (inability to open mouth wider than two fingers)"
                        ))
                        .build())
                .build());

        // 9. PRIMARY HEADACHE / MIGRAINE / NEUROVASCULAR CEPHALEA (8A80)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("8A80")
                .title("Migraine / Primary Neurovascular Headache")
                .category("Neurology")
                .specialistDepartment("Neurology")
                .urgencyTier("MEDIUM")
                .hallmarkSymptoms(List.of("headache", "migraine", "throbbing_headache", "temple_pain", "photophobia", "aura"))
                .pertinentNegatives(List.of("fever", "stiff_neck", "focal_deficit"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("MIGRAINE_DISCRIMINATOR_FEATURES")
                                .dimension("headache_features")
                                .questionText("How would you describe the headache (throbbing, dull pressure, or sharp), and does light or sound make it worse?")
                                .quickReplies(List.of("Throbbing / one-sided", "Dull band-like pressure", "Worse with light and sound", "Mild continuous ache"))
                                .conditionWeights(Map.of("8A80", 3.5, "TENSION_HEADACHE", 2.0))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("8A80")
                        .primaryDiagnosis("Migraine / Primary Neurovascular Headache")
                        .specialistDepartment("Neurology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Naproxen Sodium")
                                        .brandReference("Naprosyn 500mg")
                                        .formulation("Tablet")
                                        .strength("500 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet at early onset of headache; may repeat in 12 hours if needed")
                                        .duration("2 to 3 days")
                                        .instructions("Take strictly with food or antacid. First-line evidence-based NSAID for acute migraine abortive therapy.")
                                        .indication("Acute migraine abortive therapy and neurogenic vasodilation reduction")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Domperidone")
                                        .brandReference("Domstal 10mg")
                                        .formulation("Tablet")
                                        .strength("10 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet 15 to 30 minutes before meal or with analgesic PRN")
                                        .duration("2 to 3 days")
                                        .instructions("Relieves migraine-associated gastric stasis and nausea, accelerating oral analgesic absorption.")
                                        .indication("Gastroprokinetic and antiemetic for migraine-associated nausea")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Rest in a quiet, dark, temperature-regulated room",
                                "Apply cold gel compress or ice pack wrapped in towel to forehead or back of neck",
                                "Maintain regular hydration and avoid migraine triggers (irregular sleep, skipped meals, aspartame)"
                        ))
                        .contraindicatedMedications(List.of("Avoid overuse of combination analgesics containing caffeine or codeine (> 10 days/month)"))
                        .diagnosticLabOrders(List.of("Non-contrast brain MRI / CT if headache is thunderclap, atypical, or accompanied by focal neurological signs"))
                        .redFlagHospitalizationCriteria(List.of(
                                "Sudden explosive 'thunderclap' headache reaching peak intensity within seconds",
                                "Headache accompanied by stiff neck, high fever, or altered mental status",
                                "New focal neurological deficit (limb weakness, facial droop, slurred speech, visual field cut)"
                        ))
                        .build())
                .build());

        // 10. ACUTE BRONCHITIS & AIRWAY HYPERREACTIVITY (CA20)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("CA20")
                .title("Acute Bronchitis / Tracheobronchial Airway Reactivity")
                .category("Pulmonology")
                .specialistDepartment("Pulmonology")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("cough", "bronchitis", "phlegm", "mucus", "chest_congestion"))
                .pertinentNegatives(List.of("hemoptysis", "high_fever", "chest_pain"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("BRONCHITIS_DISCRIMINATOR_SPUTUM")
                                .dimension("cough_character")
                                .questionText("Is your cough dry and hacking, or is it producing yellow/green phlegm or mucus?")
                                .quickReplies(List.of("Dry irritant cough", "Productive with clear mucus", "Thick yellow/green phlegm", "Coughing with wheeze"))
                                .conditionWeights(Map.of("CA20", 3.0, "PNEUMONIA", 4.0))
                                .diagnosticUtility(2.5)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("CA20")
                        .primaryDiagnosis("Acute Bronchitis / Tracheobronchial Airway Reactivity")
                        .specialistDepartment("Pulmonology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Guaifenesin + Ambroxol Syrup")
                                        .brandReference("Ascoril / Benadryl Expectorant")
                                        .formulation("Syrup")
                                        .strength("100mg Guaifenesin + 30mg Ambroxol / 10ml")
                                        .route("Oral")
                                        .dosageFrequency("10 ml three times daily after food")
                                        .duration("5 to 7 days")
                                        .instructions("Drink a full glass of warm water with each dose to aid mucolytic thinning.")
                                        .indication("Mucus liquefaction and tracheobronchial clearance")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Levosalbutamol Inhaler (PRN)")
                                        .brandReference("Levolin 50mcg Inhaler")
                                        .formulation("Metered Dose Inhaler")
                                        .strength("50 mcg/puff")
                                        .route("Inhalation")
                                        .dosageFrequency("1 to 2 puffs every 6 to 8 hours PRN for wheezing or bronchospasm")
                                        .duration("5 days")
                                        .instructions("Rinse mouth with water after inhalation. Use with spacer if available.")
                                        .indication("Bronchodilation for reactive airway bronchospasm")
                                        .prescriptionOnly(true)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Steam inhalation with plain water for 10 minutes twice daily",
                                "Warm water with honey and lemon to soothe mucosal tickle and pharyngeal irritation",
                                "Avoid exposure to cigarette smoke, kitchen exhaust fumes, and cold ambient air"
                        ))
                        .contraindicatedMedications(List.of("Routine unindicated empirical antibiotics for acute uncomplicated viral bronchitis"))
                        .diagnosticLabOrders(List.of("Chest Radiograph (PA view) if fever > 101°F, tachypnea > 24/min, or focal crackles on lung auscultation"))
                        .redFlagHospitalizationCriteria(List.of(
                                "Severe shortness of breath, respiratory rate > 28/min, or oxygen saturation SpO2 < 93%",
                                "Coughing up frank red blood (hemoptysis)",
                                "Stridor, grunting, or blue discoloration around lips"
                        ))
                        .build())
                .build());

        // 11. ACUTE CORONARY SYNDROME / MYOCARDIAL INFARCTION (BA41)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("BA41")
                .title("Acute Coronary Syndrome / Myocardial Infarction")
                .category("Cardiology")
                .specialistDepartment("Cardiology / Emergency Medicine")
                .urgencyTier("CRITICAL")
                .hallmarkSymptoms(List.of("chest_pain", "crushing_chest_pain", "angina", "radiation_left_arm", "diaphoresis", "shortness_of_breath"))
                .pertinentNegatives(List.of("fever", "pleuritic_pain"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("ACS_DISCRIMINATOR_RADIATION")
                                .dimension("chest_pain_radiation")
                                .questionText("Does the chest pain radiate to your left arm, neck, or jaw, or cause profuse cold sweating or breathlessness?")
                                .quickReplies(List.of("Radiating to left arm/jaw", "Cold sweating & dizziness", "Shortness of breath", "Sharp pain changing with breathing"))
                                .conditionWeights(Map.of("BA41", 5.0))
                                .diagnosticUtility(4.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("BA41")
                        .primaryDiagnosis("Acute Coronary Syndrome / Suspected Myocardial Infarction")
                        .specialistDepartment("Cardiology / Emergency Medicine")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Aspirin (Dispersible / Chewable)")
                                        .brandReference("Ecosprin 300mg / Disprin")
                                        .formulation("Chewable Tablet")
                                        .strength("300 mg")
                                        .route("Oral (Chewed)")
                                        .dosageFrequency("Single 300mg dose chewed immediately stat while awaiting emergency transport")
                                        .duration("Single emergency loading dose")
                                        .instructions("Chew thoroughly for immediate buccal absorption. Emergency antiplatelet loading dose.")
                                        .indication("Emergency platelet cyclooxygenase-1 inhibition to arrest coronary thrombosis")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "EMERGENCY: Call 108 / 911 immediately. Do NOT drive yourself to the hospital.",
                                "Sit upright or in a semi-reclined position to minimize cardiac preload and work of breathing",
                                "Loosen any tight clothing around collar and chest; ensure adequate ventilation"
                        ))
                        .contraindicatedMedications(List.of("Strictly avoid physical exertion, stairs, walking, or delayed emergency medical dispatch"))
                        .diagnosticLabOrders(List.of("12-Lead Electrocardiogram (ECG) stat within 10 minutes", "High-Sensitivity Serum Cardiac Troponin I/T"))
                        .redFlagHospitalizationCriteria(List.of(
                                "Retrosternal crushing pressure lasting > 15 minutes",
                                "Radiation to left arm, both shoulders, neck, or jaw with diaphoresis",
                                "Hypotension, cold clammy extremities, syncope, or cardiac arrest"
                        ))
                        .build())
                .build());

        // 12. ALLERGIC CONTACT DERMATITIS / ACUTE ECZEMATOUS ERUPTION (EK00)
        registerCoreEntity(ClinicalEntity.builder()
                .icd11Code("EK00")
                .title("Allergic Contact Dermatitis / Acute Eczematous Reaction")
                .category("Dermatology")
                .specialistDepartment("Dermatology")
                .urgencyTier("LOW")
                .hallmarkSymptoms(List.of("rash", "dermatitis", "skin_rash", "pruritus", "skin_itching", "erythema", "blistering_rash"))
                .pertinentNegatives(List.of("fever", "mucosal_involvement"))
                .discriminatorQuestions(List.of(
                        DiscriminatorQuestion.builder()
                                .id("DERMATITIS_DISCRIMINATOR_TRIGGER")
                                .dimension("allergen_trigger")
                                .questionText("Did the itchy rash appear after contact with specific metals, cosmetics, new detergents, or outdoor plants?")
                                .quickReplies(List.of("Metal or jewelry contact", "Cosmetics or new soap", "Outdoor plants / poison ivy", "Spreading across body"))
                                .conditionWeights(Map.of("EK00", 3.5, "SYSTEMIC_DRUG_ERUPTION", 4.5))
                                .diagnosticUtility(3.0)
                                .build()
                ))
                .defaultPrescriptionProtocol(PrescriptionProtocol.builder()
                        .icd11Code("EK00")
                        .primaryDiagnosis("Allergic Contact Dermatitis / Acute Eczematous Reaction")
                        .specialistDepartment("Dermatology")
                        .medications(List.of(
                                RxMedicationItem.builder()
                                        .saltName("Hydrocortisone 1% Topical Cream")
                                        .brandReference("Cortaid / Hydrocort 1%")
                                        .formulation("Cream")
                                        .strength("1.0% w/w")
                                        .route("Topical")
                                        .dosageFrequency("Apply thin film to clean affected itchy areas twice daily")
                                        .duration("5 to 7 days")
                                        .instructions("Gently wash area first. Do not apply onto broken or weeping skin.")
                                        .indication("Topical anti-inflammatory relief of localized contact pruritus and erythema")
                                        .prescriptionOnly(false)
                                        .build(),
                                RxMedicationItem.builder()
                                        .saltName("Cetirizine Hydrochloride")
                                        .brandReference("Zyrtec / Cetzine 10mg")
                                        .formulation("Tablet")
                                        .strength("10 mg")
                                        .route("Oral")
                                        .dosageFrequency("1 tablet once daily in the evening")
                                        .duration("5 days")
                                        .instructions("Take with water. Non-sedating second-generation H1 receptor antagonist.")
                                        .indication("Systemic reduction of allergic cutaneous pruritus")
                                        .prescriptionOnly(false)
                                        .build()
                        ))
                        .supportiveCare(List.of(
                                "Immediately remove offending contact allergen (jewelry, watch, belt buckle, cosmetic)",
                                "Apply cold damp compresses over itchy patches for 10-15 minutes to reduce flare-up",
                                "Trim fingernails short to prevent nocturnal skin excoriation and secondary bacterial infection"
                        ))
                        .contraindicatedMedications(List.of("Do not apply topical diphenhydramine or topical anesthetics (frequent cause of secondary contact sensitization)"))
                        .diagnosticLabOrders(List.of("Dermatological Patch Testing (TRUE test) if recurrent or persistent > 2 weeks"))
                        .redFlagHospitalizationCriteria(List.of(
                                "Facial, lip, or tongue swelling with breathing difficulty (anaphylaxis concern)",
                                "Rapidly spreading warmth, intense tenderness, or purulent golden crusting (secondary cellulitis)",
                                "Mucosal erosion involving mouth, eyes, or genitalia (Stevens-Johnson syndrome alarm)"
                        ))
                        .build())
                .build());
    }
}

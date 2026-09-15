package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.model.feature.StructuredClinicalFeature;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.FactPresence;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction Engine V2:
 * Deterministic, clause-level clinical feature extractor with strict attribute binding.
 *
 * Guarantees:
 * 1. Clause segmentation prevents cross-pollination of temporal, negation, and severity modifiers.
 * 2. Tri-state presence is preserved: PRESENT, ABSENT_DENIED, UNKNOWN.
 * 3. Sputum characteristics (yellow, green, clear, productive) are bound to cough and sputum features.
 * 4. Extracted features reliably enter both state.getSymptoms() and state.getNegatedFindings().
 * 5. Extraction confidence is explicitly separated from disease probability.
 */
@Slf4j
@Component
public class ClinicalFeatureExtractorV2 {

    // Clause boundary delimiters: conjunctions, commas, semicolons, sentence terminators
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile(
            "(?i)\\s*(?:\\.|;|\\band\\s+then\\b|\\band\\b|\\bbut\\b|\\bhowever\\b|\\balthough\\b|\\bwhile\\b|\\byet\\b|,|\\n)\\s*"
    );

    // Negation patterns within a clause
    private static final Pattern NEGATION_PATTERN = Pattern.compile(
            "(?i)\\b(no|not|without|denies|denying|never|neither|nor|negative\\s+for|don't\\s+have|dont\\s+have|haven't\\s+had|havent\\s+had)\\b"
    );

    // Uncertainty patterns (UNKNOWN status)
    private static final Pattern UNCERTAINTY_PATTERN = Pattern.compile(
            "(?i)\\b(not\\s+sure|unsure|maybe|might\\s+be|don't\\s+know|dont\\s+know|possibly|hard\\s+to\\s+say)\\b"
    );

    // Clause-bound temporal patterns
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:for|past|last)?\\s*(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|a|several|few)\\s*(days?|hours?|weeks?|months?)\\b"
    );

    private static final Pattern ONSET_PATTERN = Pattern.compile(
            "(?i)\\b(started\\s*(?:today|yesterday|recently|just\\s*now)|since\\s*(?:yesterday|today|last\\s*night|this\\s*morning)|began\\s*(?:today|yesterday|recently)|today|yesterday|just\\s*started|sudden(?:ly)?|gradual(?:ly)?)\\b"
    );

    private static final Pattern PROGRESSION_PATTERN = Pattern.compile(
            "(?i)\\b(getting\\s*worse|worsening|became\\s*worse|more\\s*severe|getting\\s*better|improving|stable|constant|comes\\s*and\\s*goes|intermittent|persistent|continuous)\\b"
    );

    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
            "(?i)\\b(mild|moderate|severe|critical|very\\s*severe|unbearable|slight|intense|sharp|dull|throbbing|crushing)\\b"
    );

    private static final Pattern ANATOMICAL_SITE_PATTERN = Pattern.compile(
            "(?i)\\b(chest|retrosternal|throat|sinus|sinuses|ear|ears|stomach|abdomen|belly|back|neck|head|pelvis|groin|joint|knee|ankle)\\b"
    );

    // ─── Concept Matchers ──────────────────────────────────────────────────────
    private static final Pattern COUGH_PATTERN = Pattern.compile(
            "(?i)\\b(cough|coughing|coughed|chesty\\s*cough|dry\\s*cough|wet\\s*cough|barking\\s*cough|hacking\\s*cough)\\b"
    );

    private static final Pattern SPUTUM_PATTERN = Pattern.compile(
            "(?i)\\b(phlegm|mucus|sputum|yellow\\s*(?:phlegm|mucus|sputum)|green\\s*(?:phlegm|mucus|sputum)|yellowish|greenish|purulent|clear\\s*(?:mucus|phlegm)|white\\s*(?:mucus|phlegm)|blood\\s*(?:in\\s*phlegm|specked)|hemoptysis)\\b"
    );

    private static final Pattern WHEEZE_PATTERN = Pattern.compile(
            "(?i)\\b(wheeze|wheezing|whistling\\s*(?:when\\s*i\\s*breathe|in\\s*chest|sound)|whistling\\s*breath|stridor)\\b"
    );

    private static final Pattern DYSPNEA_PATTERN = Pattern.compile(
            "(?i)\\b(short(?:ness)?\\s*of\\s*breath|breathless(?:ness)?|trouble\\s*breathing|difficulty\\s*breathing|hard\\s*to\\s*breathe|struggling\\s*for\\s*breath|can't\\s*catch\\s*my\\s*breath|cant\\s*catch\\s*my\\s*breath|air\\s*hunger|dyspnea)\\b"
    );

    private static final Pattern CHEST_TIGHT_PATTERN = Pattern.compile(
            "(?i)\\b(chest\\s*(?:tightness|feels\\s*tight|tight|congestion|heavy|heaviness|discomfort|soreness)|tight\\s*chest)\\b"
    );

    private static final Pattern FEVER_PATTERN = Pattern.compile(
            "(?i)\\b(fever|high\\s*fever|chills|rigors|shivering|high\\s*temp(?:erature)?|temperature|febrile|pyrexia)\\b"
    );

    private static final Pattern SORE_THROAT_PATTERN = Pattern.compile(
            "(?i)\\b(sore\\s*throat|throat\\s*pain|scratchy\\s*throat|pharyngitis|red\\s*throat|pharyngeal\\s*(?:pain|erythema)|throat\\s*hurts)\\b"
    );

    private static final Pattern ODYNOPHAGIA_PATTERN = Pattern.compile(
            "(?i)\\b(painful\\s*swallowing|hurts?\\s*to\\s*swallow|hurts?\\s*when\\s*i\\s*swallow|pain\\s*when\\s*swallowing|odynophagia|pain\\s*on\\s*swallowing)\\b"
    );

    private static final Pattern DYSPHAGIA_PATTERN = Pattern.compile(
            "(?i)\\b(difficulty\\s*swallowing|hard\\s*to\\s*swallow|trouble\\s*swallowing|dysphagia)\\b"
    );

    private static final Pattern INABILITY_TO_SWALLOW_PATTERN = Pattern.compile(
            "(?i)\\b(can't\\s*swallow|cannot\\s*swallow|unable\\s*to\\s*swallow|cant\\s*swallow)\\b"
    );

    private static final Pattern FUNCTIONAL_SWALLOW_PATTERN = Pattern.compile(
            "(?i)\\b(can\\s*(?:still\\s*)?drink(?:\s*normally|\s*water)?|able\\s*to\\s*drink|swallowing\\s*is\\s*fine|can\\s*swallow\\s*liquids?|able\\s*to\\s*swallow|can\\s*drink\\s*normally)\\b"
    );

    private static final Pattern DROOLING_PATTERN = Pattern.compile(
            "(?i)\\b(drooling|drool|sialorrhea|spitting\\s*saliva|can't\\s*swallow\\s*(?:my\\s*)?saliva|cannot\\s*swallow\\s*(?:my\\s*)?saliva|unable\\s*to\\s*swallow\\s*saliva|cant\\s*swallow\\s*(?:my\\s*)?saliva)\\b"
    );

    private static final Pattern TONSILLAR_EXUDATE_PATTERN = Pattern.compile(
            "(?i)\\b(tonsillar\\s*exudate|exudate|white\\s*patches?\\s*(?:on\\s*tonsils?|in\\s*throat)|white\\s*spots?\\s*(?:on\\s*tonsils?|in\\s*throat)|pus\\s*on\\s*tonsils?|tonsillar\\s*pus)\\b"
    );

    private static final Pattern TONSILLAR_SWELLING_PATTERN = Pattern.compile(
            "(?i)\\b(tonsillar\\s*(?:swelling|enlargement|hypertrophy)|swollen\\s*tonsils?|enlarged\\s*tonsils?|red\\s*tonsils?)\\b"
    );

    private static final Pattern CERVICAL_ADENOPATHY_PATTERN = Pattern.compile(
            "(?i)\\b(tender\\s*(?:and\\s*)?(?:swollen\\s*)?(?:neck\\s*)?(?:glands|lymph\\s*nodes?)|swollen\\s*(?:and\\s*)?(?:tender\\s*)?(?:neck\\s*)?(?:glands|lymph\\s*nodes?)|swollen\\s*glands|anterior\\s*cervical\\s*(?:tenderness|nodes?|adenopathy)|painful\\s*(?:neck\\s*)?nodes?|lumps?\\s*in\\s*neck)\\b"
    );

    private static final Pattern VOICE_CHANGE_PATTERN = Pattern.compile(
            "(?i)\\b(muffled\\s*voice|hot\\s*potato\\s*voice|voice\\s*change|hoarse(?:ness)?|raspy\\s*voice|lost\\s*my\\s*voice)\\b"
    );

    private static final Pattern STRIDOR_PATTERN = Pattern.compile(
            "(?i)\\b(stridor|noisy\\s*breathing|high[- ]pitched\\s*breathing|noisy\\s*airway)\\b"
    );

    private static final Pattern HEADACHE_PATTERN = Pattern.compile(
            "(?i)\\b(headache|head\\s*pain|migraine|throbbing\\s*head|sar\\s*dard)\\b"
    );

    private static final Pattern ABDOMINAL_PAIN_PATTERN = Pattern.compile(
            "(?i)\\b(stomach\\s*ache|stomach\\s*pain|abdominal\\s*pain|belly\\s*pain|cramps|cramping|tummy\\s*ache|pet\\s*dard)\\b"
    );

    private static final Pattern NAUSEA_PATTERN = Pattern.compile(
            "(?i)\\b(nausea|nauseous|feel\\s*sick|queasy|ji\\s*ghabrana)\\b"
    );

    private static final Pattern VOMIT_PATTERN = Pattern.compile(
            "(?i)\\b(vomit|vomiting|threw\\s*up|throwing\\s*up|emesis)\\b"
    );

    private static final Pattern DIARRHEA_PATTERN = Pattern.compile(
            "(?i)\\b(diarrhea|loose\\s*motions?|watery\\s*stools?|dast)\\b"
    );

    private static final Pattern RHINORRHEA_PATTERN = Pattern.compile(
            "(?i)\\b(runny\\s*nose|nasal\\s*discharge|rhinorrhea|sneezing|congestion|stuffy\\s*nose|blocked\\s*nose)\\b"
    );

    private static final Pattern JOINT_PAIN_PATTERN = Pattern.compile(
            "(?i)\\b(joint\\s*pain|arthralgia|knee\\s*pain|shoulder\\s*pain|swollen\\s*joint)\\b"
    );

    private static final Pattern DIZZINESS_PATTERN = Pattern.compile(
            "(?i)\\b(dizzy|dizziness|lightheaded|lightheadedness|vertigo|chakkar)\\b"
    );

    /**
     * Extracts structured clinical features with clause-level attribute binding.
     */
    public List<StructuredClinicalFeature> extractFeatures(String text, int turn) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<StructuredClinicalFeature> extractedFeatures = new ArrayList<>();
        String[] clauses = CLAUSE_SPLITTER.split(text);

        for (String clause : clauses) {
            String c = clause.trim();
            if (c.isEmpty()) continue;

            // Determine clause-level epistemic presence: uncertainty takes precedence over simple negation words
            boolean isClauseUncertain = UNCERTAINTY_PATTERN.matcher(c).find();
            boolean isClauseNegated = !isClauseUncertain && NEGATION_PATTERN.matcher(c).find();
            FactPresence defaultPresence = isClauseUncertain ? FactPresence.UNKNOWN
                    : (isClauseNegated ? FactPresence.ABSENT_DENIED : FactPresence.PRESENT);

            // Extract clause-bound modifiers
            String clauseDuration = extractMatch(DURATION_PATTERN, c);
            String clauseOnset = extractMatch(ONSET_PATTERN, c);
            String clauseProgression = extractMatch(PROGRESSION_PATTERN, c);
            String clauseSeverity = extractMatch(SEVERITY_PATTERN, c);
            String clauseAnatomy = extractMatch(ANATOMICAL_SITE_PATTERN, c);

            // Match and bind features within this clause
            matchAndBind(c, COUGH_PATTERN, "cough", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseAnatomy, turn, extractedFeatures);
            matchAndBind(c, SPUTUM_PATTERN, "sputum_production", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "CHEST", turn, extractedFeatures);
            matchAndBind(c, WHEEZE_PATTERN, "wheezing", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "CHEST", turn, extractedFeatures);
            matchAndBind(c, DYSPNEA_PATTERN, "dyspnea", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "CHEST", turn, extractedFeatures);
            matchAndBind(c, CHEST_TIGHT_PATTERN, "chest_symptoms", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "CHEST", turn, extractedFeatures);
            matchAndBind(c, FEVER_PATTERN, "fever", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, null, turn, extractedFeatures);
            matchAndBind(c, SORE_THROAT_PATTERN, "sore_throat", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, ODYNOPHAGIA_PATTERN, "odynophagia", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, DYSPHAGIA_PATTERN, "dysphagia", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, INABILITY_TO_SWALLOW_PATTERN, "inability_to_swallow", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, FUNCTIONAL_SWALLOW_PATTERN, "preserved_swallowing", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, DROOLING_PATTERN, "drooling", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, TONSILLAR_EXUDATE_PATTERN, "tonsillar_exudate", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, TONSILLAR_SWELLING_PATTERN, "tonsillar_swelling", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, CERVICAL_ADENOPATHY_PATTERN, "cervical_adenopathy", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "NECK", turn, extractedFeatures);
            matchAndBind(c, VOICE_CHANGE_PATTERN, "voice_change", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "THROAT", turn, extractedFeatures);
            matchAndBind(c, STRIDOR_PATTERN, "stridor", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "AIRWAY", turn, extractedFeatures);
            matchAndBind(c, HEADACHE_PATTERN, "headache", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "HEAD", turn, extractedFeatures);
            matchAndBind(c, ABDOMINAL_PAIN_PATTERN, "abdominal_pain", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "ABDOMEN", turn, extractedFeatures);
            matchAndBind(c, NAUSEA_PATTERN, "nausea", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "ABDOMEN", turn, extractedFeatures);
            matchAndBind(c, VOMIT_PATTERN, "vomiting", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "ABDOMEN", turn, extractedFeatures);
            matchAndBind(c, DIARRHEA_PATTERN, "diarrhea", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "ABDOMEN", turn, extractedFeatures);
            matchAndBind(c, RHINORRHEA_PATTERN, "cold_symptoms", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "NOSE", turn, extractedFeatures);
            matchAndBind(c, JOINT_PAIN_PATTERN, "joint_pain", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, clauseAnatomy != null ? clauseAnatomy : "JOINT", turn, extractedFeatures);
            matchAndBind(c, DIZZINESS_PATTERN, "dizziness", defaultPresence, clauseDuration, clauseOnset, clauseProgression, clauseSeverity, "HEAD", turn, extractedFeatures);
        }

        // Post-processing: If functional preserved swallowing is reported present, do not allow inability_to_swallow
        boolean preservedSwallow = extractedFeatures.stream()
                .anyMatch(f -> "preserved_swallowing".equals(f.getCanonicalConcept()) && f.isPresent());
        if (preservedSwallow) {
            extractedFeatures.removeIf(f -> "inability_to_swallow".equals(f.getCanonicalConcept()) && f.isPresent());
        }

        return extractedFeatures;
    }

    private void matchAndBind(
            String clauseText,
            Pattern conceptPattern,
            String canonicalConcept,
            FactPresence presence,
            String duration,
            String onset,
            String progression,
            String severity,
            String anatomy,
            int turn,
            List<StructuredClinicalFeature> collector) {

        Matcher m = conceptPattern.matcher(clauseText);
        if (m.find()) {
            String rawMention = m.group(1);
            List<String> qualifiers = new ArrayList<>();

            // Specific qualifier enrichment
            if ("cough".equals(canonicalConcept)) {
                if (clauseText.contains("phlegm") || clauseText.contains("mucus") || clauseText.contains("yellow") || clauseText.contains("green") || clauseText.contains("wet") || clauseText.contains("productive") || clauseText.contains("chesty")) {
                    qualifiers.add("productive");
                }
                if (clauseText.contains("dry") || clauseText.contains("hacking")) {
                    qualifiers.add("dry");
                }
            } else if ("sputum_production".equals(canonicalConcept)) {
                if (clauseText.contains("yellow")) qualifiers.add("yellow");
                if (clauseText.contains("green")) qualifiers.add("green");
                if (clauseText.contains("purulent")) qualifiers.add("purulent");
                if (clauseText.contains("clear")) qualifiers.add("clear");
                if (clauseText.contains("white")) qualifiers.add("white");
                if (clauseText.contains("blood") || clauseText.contains("hemoptysis")) qualifiers.add("hemoptysis");
            } else if ("fever".equals(canonicalConcept)) {
                if (clauseText.contains("high") || clauseText.contains("chills") || clauseText.contains("rigor")) qualifiers.add("high_grade");
                if (clauseText.contains("mild") || clauseText.contains("low")) qualifiers.add("low_grade");
            }

            double confidence = (presence == FactPresence.UNKNOWN) ? 0.0 : 1.0;

            StructuredClinicalFeature feat = StructuredClinicalFeature.builder()
                    .canonicalConcept(canonicalConcept)
                    .rawMention(rawMention)
                    .presence(presence)
                    .duration(duration)
                    .onset(onset)
                    .progression(normalizeProgression(progression))
                    .severity(severity != null ? severity.toUpperCase(Locale.ROOT) : null)
                    .anatomicalSite(anatomy != null ? anatomy.toUpperCase(Locale.ROOT) : null)
                    .qualifiers(qualifiers)
                    .extractionConfidence(confidence)
                    .sourceTurn(turn)
                    .build();

            collector.add(feat);
        }
    }

    private String normalizeProgression(String raw) {
        if (raw == null) return null;
        String r = raw.toLowerCase(Locale.ROOT);
        if (r.contains("worse") || r.contains("worsening")) return "WORSENING";
        if (r.contains("better") || r.contains("improv")) return "IMPROVING";
        if (r.contains("stable")) return "STABLE";
        if (r.contains("intermittent") || r.contains("comes and goes")) return "INTERMITTENT";
        if (r.contains("persistent") || r.contains("constant") || r.contains("continuous")) return "PERSISTENT";
        return raw.toUpperCase(Locale.ROOT);
    }

    private String extractMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(0).trim();
        }
        return null;
    }

    /**
     * Executes feature extraction and updates ClinicalConversationState with strict epistemic status.
     */
    public void updateStateWithFeatures(List<StructuredClinicalFeature> features, ClinicalConversationState state, int turn) {
        if (features == null || state == null) return;

        for (StructuredClinicalFeature f : features) {
            String concept = f.getCanonicalConcept();

            if (f.isPresent()) {
                String val = !f.getQualifiers().isEmpty() ? String.join("_", f.getQualifiers()) : "present";
                ClinicalFact fact = ClinicalFact.present(concept, val, turn);
                fact.setDuration(f.getDuration());
                fact.setOnset(f.getOnset());
                fact.setSeverity(f.getSeverity());
                state.getSymptoms().put(concept, fact);
                state.addFact(concept, fact);

                // Populate timeline duration if present
                if (f.getDuration() != null) {
                    state.getTimeline().put("duration", f.getDuration());
                    state.addFact("duration", ClinicalFact.present("duration", f.getDuration(), turn));
                }
                if (f.getProgression() != null) {
                    state.getTimeline().put("progression", f.getProgression());
                    state.addFact("progression", ClinicalFact.present("progression", f.getProgression(), turn));
                    if ("GETTING WORSE".equals(f.getProgression()) || "WORSENING".equals(f.getProgression())) {
                        state.setSymptomTrajectory("WORSENING");
                    }
                }
                if (f.getSeverity() != null && state.getSeverity() == null) {
                    state.setSeverity(f.getSeverity());
                }

                // If symptom was previously negated, remove from negatedFindings
                state.getNegatedFindings().remove(concept);

            } else if (f.isDenied()) {
                ClinicalFact deniedFact = ClinicalFact.denied(concept, turn);
                state.addFact(concept, deniedFact);
                state.getNegatedFindings().add(concept);
                // Remove from active symptoms if previously asserted
                state.getSymptoms().remove(concept);

            } else if (f.isUnknown()) {
                ClinicalFact unknownFact = ClinicalFact.unknown(concept);
                state.addFact(concept, unknownFact);
                state.getUnknownFacts().add(concept);
            }
        }
    }
}

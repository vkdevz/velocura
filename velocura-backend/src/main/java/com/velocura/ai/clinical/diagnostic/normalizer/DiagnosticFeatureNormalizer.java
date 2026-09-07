package com.velocura.ai.clinical.diagnostic.normalizer;

import com.velocura.ai.clinical.diagnostic.model.*;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ProvenanceSource;
import com.velocura.medicalknowledge.model.MedicalConcept;
import com.velocura.medicalknowledge.model.MedicalConceptType;
import com.velocura.medicalknowledge.service.MedicalKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DiagnosticFeatureNormalizer:
 * Maps patient colloquial vocabulary into canonical medical concepts via MKE and clinical dictionaries.
 * Distinguishes positive findings from explicitly denied findings and unmentioned findings.
 * Preserves strict epistemic provenance (PATIENT_REPORTED vs SYSTEM_INFERRED vs LAB_CONFIRMED).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosticFeatureNormalizer {

    private final MedicalKnowledgeService medicalKnowledgeService;

    public static class ClinicalConceptMapping {
        final String canonicalName;
        final String defaultConceptId;
        final List<String> patterns;

        ClinicalConceptMapping(String canonicalName, String defaultConceptId, List<String> patterns) {
            this.canonicalName = canonicalName;
            this.defaultConceptId = defaultConceptId;
            this.patterns = patterns;
        }
    }

    private static final List<ClinicalConceptMapping> CORE_MAPPINGS = List.of(
            new ClinicalConceptMapping("Dyspnea", "CON-SYM-DYSPNEA", List.of(
                    "can't catch my breath", "cannot catch my breath", "short of breath", "shortness of breath",
                    "trouble breathing", "difficulty breathing", "hard to breathe", "breathless", "out of breath",
                    "dyspnea", "gasping"
            )),
            new ClinicalConceptMapping("Vomiting", "CON-SYM-VOMITING", List.of(
                    "throwing up", "threw up", "throw up", "puking", "puked", "vomiting", "vomit", "emesis"
            )),
            new ClinicalConceptMapping("Palpitations", "CON-SYM-PALPITATIONS", List.of(
                    "heart feels like racing", "heart racing", "fluttering chest", "chest fluttering",
                    "heart palpitations", "racing heart", "pounding heartbeat", "palpitations", "skipped a beat"
            )),
            new ClinicalConceptMapping("Headache", "CON-SYM-HEADACHE", List.of(
                    "head is killing me", "splitting headache", "head hurts", "throbbing head",
                    "headache", "head ache", "migraine", "severe head pain", "tension headache"
            )),
            new ClinicalConceptMapping("Dysuria", "CON-SYM-DYSURIA", List.of(
                    "pee burns", "burning when i pee", "hurts to pee", "pain when urinating",
                    "burning urination", "painful urination", "dysuria", "stinging urination"
            )),
            new ClinicalConceptMapping("Diarrhea", "CON-SYM-DIARRHEA", List.of(
                    "loose motions", "the runs", "loose stools", "watery stools", "diarrhea", "diarrhoea"
            )),
            new ClinicalConceptMapping("Fever", "CON-SYM-FEVER", List.of(
                    "fever", "high temp", "high temperature", "running a temperature", "febrile",
                    "pyrexia", "burning up", "hot to touch"
            )),
            new ClinicalConceptMapping("Cough", "CON-SYM-COUGH", List.of(
                    "cough", "coughing", "hacking cough", "dry cough", "productive cough", "coughing up phlegm", "tussis"
            )),
            new ClinicalConceptMapping("Chest Pain", "CON-SYM-CHEST-PAIN", List.of(
                    "chest pain", "tightness in chest", "chest tightness", "chest pressure", "pleuritic chest pain",
                    "pain in my chest", "heaviness in chest", "crushing chest pain", "angina"
            )),
            new ClinicalConceptMapping("Sore Throat", "CON-SYM-SORE-THROAT", List.of(
                    "sore throat", "throat hurts", "scratchy throat", "painful swallowing", "pharyngitis"
            )),
            new ClinicalConceptMapping("Nausea", "CON-SYM-NAUSEA", List.of(
                    "nausea", "feeling sick", "queasy", "nauseous", "upset stomach"
            )),
            new ClinicalConceptMapping("Dizziness", "CON-SYM-DIZZINESS", List.of(
                    "dizziness", "dizzy", "lightheaded", "lightheadedness", "room spinning", "vertigo", "feeling faint"
            )),
            new ClinicalConceptMapping("Abdominal Pain", "CON-SYM-ABDOMINAL-PAIN", List.of(
                    "abdominal pain", "stomach ache", "belly ache", "stomach cramps", "cramps in stomach",
                    "belly pain", "gut pain", "gastric pain"
            )),
            new ClinicalConceptMapping("Fatigue", "CON-SYM-FATIGUE", List.of(
                    "fatigue", "exhausted", "extremely tired", "lethargy", "weakness", "no energy"
            )),
            new ClinicalConceptMapping("Chills", "CON-SYM-CHILLS", List.of(
                    "chills", "shivering", "shivers", "rigors", "cold sweats"
            )),
            new ClinicalConceptMapping("Ankle Pain", "CON-SYM-ANKLE-PAIN", List.of(
                    "ankle pain", "twisted ankle", "twisted my ankle", "sprained ankle", "ankle sprain", "hurt my ankle", "pain in my ankle"
            )),
            new ClinicalConceptMapping("Joint Pain", "CON-SYM-JOINT-PAIN", List.of(
                    "joint pain", "arthralgia", "aching joints", "stiff joints"
            ))
    );

    private static final Pattern TIMELINE_PATTERN = Pattern.compile(
            "(?i)\\b(started\\s*(?:today|yesterday|recently|just\\s*now)|since\\s*(?:yesterday|today|last\\s*night|morning)|for\\s*\\d+\\s*(?:days?|hours?|weeks?)|\\d+\\s*(?:days?|hours?|weeks?|mins?|minutes?)|two\\s*days\\s*ago|past\\s*\\d+.*days?)\\b"
    );

    private static final Pattern SEVERITY_PATTERN = Pattern.compile(
            "(?i)\\b(mild|moderate|severe|critical|very\\s*severe|unbearable|manageable|sharp|dull|throbbing|crushing)\\b"
    );

    private static final Pattern TRAJECTORY_PATTERN = Pattern.compile(
            "(?i)\\b(worsening|getting\\s*worse|improving|getting\\s*better|stable|unchanged|comes\\s*and\\s*goes|intermittent|constant|continuous)\\b"
    );

    /**
     * Extracts and normalizes clinical features from user input into the given episode.
     */
    public List<ClinicalDiagnosticFeature> extractAndNormalize(
            String text,
            ClinicalEpisode episode,
            int turnNumber,
            ProvenanceSource provenance) {

        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String lowerText = text.toLowerCase().trim();
        List<ClinicalDiagnosticFeature> extracted = new ArrayList<>();

        // Extract context attributes
        String detectedTimeline = extractRegex(TIMELINE_PATTERN, lowerText);
        SeverityGrade detectedSeverity = extractSeverity(lowerText);
        TrajectoryType detectedTrajectory = extractTrajectory(lowerText);

        for (ClinicalConceptMapping mapping : CORE_MAPPINGS) {
            boolean matchedPositive = false;
            boolean matchedDenied = false;

            for (String patternStr : mapping.patterns) {
                if (isPatternDenied(lowerText, patternStr)) {
                    matchedDenied = true;
                    break;
                } else if (isPatternPresent(lowerText, patternStr)) {
                    matchedPositive = true;
                    break;
                }
            }

            if (matchedPositive || matchedDenied) {
                String conceptId = resolveConceptId(mapping.canonicalName, mapping.defaultConceptId);
                FeaturePresence presence = matchedDenied ? FeaturePresence.ABSENT_DENIED : FeaturePresence.PRESENT;

                ClinicalDiagnosticFeature feature = ClinicalDiagnosticFeature.builder()
                        .conceptId(conceptId)
                        .canonicalName(mapping.canonicalName)
                        .value(presence == FeaturePresence.PRESENT ? "present" : "absent")
                        .valueType(ValueType.BOOLEAN)
                        .presence(presence)
                        .severity(presence == FeaturePresence.PRESENT ? detectedSeverity : SeverityGrade.UNKNOWN)
                        .duration(detectedTimeline)
                        .trajectory(presence == FeaturePresence.PRESENT ? detectedTrajectory : TrajectoryType.NEW)
                        .certainty(FeatureCertainty.REPORTED)
                        .provenance(provenance != null ? provenance : ProvenanceSource.PATIENT_REPORTED)
                        .sourceTurn(turnNumber)
                        .timestamp(System.currentTimeMillis())
                        .build();

                extracted.add(feature);
                if (episode != null) {
                    episode.addOrUpdateFeature(feature);
                }
            }
        }

        return extracted;
    }

    private boolean isPatternDenied(String text, String phrase) {
        Pattern deniedPattern = Pattern.compile(
                "(?i)\\b(no|not|without|denies|never|negative\\s+for|don't\\s+have|do\\s+not\\s+have)\\s+(?:\\w+\\s+){0,3}" + Pattern.quote(phrase) + "\\b"
        );
        return deniedPattern.matcher(text).find();
    }

    private boolean isPatternPresent(String text, String phrase) {
        Pattern presentPattern = Pattern.compile("(?i)\\b" + Pattern.quote(phrase) + "\\b");
        return presentPattern.matcher(text).find();
    }

    private String resolveConceptId(String canonicalName, String defaultId) {
        if (medicalKnowledgeService != null) {
            Optional<MedicalConcept> concept = medicalKnowledgeService.findConceptByCanonicalName(canonicalName);
            if (concept.isPresent()) {
                return concept.get().getConceptId();
            }
        }
        return defaultId;
    }

    private String extractRegex(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private SeverityGrade extractSeverity(String text) {
        String match = extractRegex(SEVERITY_PATTERN, text);
        if (match == null) return SeverityGrade.UNKNOWN;
        String m = match.toLowerCase();
        if (m.contains("critical") || m.contains("unbearable") || m.contains("crushing")) return SeverityGrade.CRITICAL;
        if (m.contains("severe")) return SeverityGrade.SEVERE;
        if (m.contains("moderate")) return SeverityGrade.MODERATE;
        if (m.contains("mild")) return SeverityGrade.MILD;
        return SeverityGrade.UNKNOWN;
    }

    private TrajectoryType extractTrajectory(String text) {
        String match = extractRegex(TRAJECTORY_PATTERN, text);
        if (match == null) return TrajectoryType.NEW;
        String m = match.toLowerCase();
        if (m.contains("worse") || m.contains("worsening")) return TrajectoryType.WORSENING;
        if (m.contains("better") || m.contains("improving")) return TrajectoryType.IMPROVING;
        if (m.contains("stable") || m.contains("unchanged") || m.contains("constant") || m.contains("continuous")) return TrajectoryType.STABLE;
        if (m.contains("comes and goes") || m.contains("intermittent")) return TrajectoryType.RECURRENT;
        return TrajectoryType.NEW;
    }
}

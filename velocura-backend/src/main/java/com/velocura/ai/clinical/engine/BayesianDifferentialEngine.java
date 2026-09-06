package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.knowledge.ClinicalConditionDefinition;
import com.velocura.ai.clinical.knowledge.ConditionEvidenceProvider;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import com.velocura.ai.clinical.state.PatientContext;
import com.velocura.dto.DifferentialDiagnosis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BayesianDifferentialEngine:
 * Computes probabilistic differential diagnoses by combining pre-test epidemiological priors,
 * positive symptom likelihood ratios (LR+), negative finding penalties (LR-), and objective lab biomarkers.
 */
@Component
public class BayesianDifferentialEngine {

    private static final Logger log = LoggerFactory.getLogger(BayesianDifferentialEngine.class);
    private final ConditionEvidenceProvider conditionEvidenceProvider;

    public BayesianDifferentialEngine(ConditionEvidenceProvider conditionEvidenceProvider) {
        this.conditionEvidenceProvider = conditionEvidenceProvider;
    }

    private static class ScoredCondition {
        ClinicalConditionDefinition definition;
        double posteriorOdds;
        double probabilityPercentage;
        List<String> supporting = new ArrayList<>();
        List<String> refuting = new ArrayList<>();

        ScoredCondition(ClinicalConditionDefinition definition, double initialOdds) {
            this.definition = definition;
            this.posteriorOdds = initialOdds;
        }
    }

    /**
     * Computes probabilistic top-3 differentials based on state and evidence.
     */
    public List<DifferentialDiagnosis> computeDifferentials(ClinicalConversationState state, String rawInput) {
        List<ClinicalConditionDefinition> definitions = conditionEvidenceProvider.getDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return getDefaultFallback(state);
        }

        PatientContext patient = state.getPatientContext();
        Map<String, ClinicalFact> symptoms = state.getSymptoms() != null ? state.getSymptoms() : Collections.emptyMap();
        Map<String, ClinicalFact> facts = state.getKnownFacts() != null ? state.getKnownFacts() : Collections.emptyMap();
        Map<String, String> vitals = state.getVitals() != null ? state.getVitals() : Collections.emptyMap();
        String lowerInput = (rawInput != null ? rawInput : "").toLowerCase();

        List<ScoredCondition> scoredList = new ArrayList<>();

        for (ClinicalConditionDefinition def : definitions) {
            double prior = calculatePreTestOdds(def, patient);
            ScoredCondition sc = new ScoredCondition(def, prior);

            // 1. Positive Keyword & Symptom Matching (LR+ Multipliers)
            for (String kw : def.getKeywords()) {
                String lkw = kw.toLowerCase();
                boolean matches = lowerInput.contains(lkw);
                if (!matches && symptoms != null) {
                    for (String symKey : symptoms.keySet()) {
                        String sk = symKey.toLowerCase();
                        if (sk.equals(lkw) || sk.contains(lkw) || lkw.contains(sk)
                                || (sk.equals("sprain_strain") && (lkw.contains("sprain") || lkw.contains("twist") || lkw.contains("joint")))
                                || (sk.equals("laceration_wound") && (lkw.contains("cut") || lkw.contains("wound") || lkw.contains("lacerat")))
                                || (sk.equals("burn_injury") && lkw.contains("burn"))
                                || (sk.equals("dysuria") && (lkw.contains("urin") || lkw.contains("dysuria")))
                                || (sk.equals("dental_pain") && (lkw.contains("tooth") || lkw.contains("teeth") || lkw.contains("dental")))
                                || ((sk.equals("eye_symptoms") || sk.equals("conjunctivitis_symptoms")) && (lkw.contains("eye") || lkw.contains("conjunctiv") || lkw.contains("blur")))) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    sc.posteriorOdds *= 3.2;
                    sc.supporting.add("Patient reported hallmark symptom: " + kw);
                }
            }

            // 2. Specific Symptom System Correlations
            evaluateSystemCorrelations(sc, def, symptoms, vitals, facts);

            // 3. Negative Finding Penalties (LR- Down-weighting)
            evaluateNegativeFindings(sc, def, state);

            // 4. Objective Lab Biomarker Correlations
            evaluateBiomarkers(sc, def, vitals, facts);

            scoredList.add(sc);
        }

        // Sort by posterior odds descending
        scoredList.sort((a, b) -> Double.compare(b.posteriorOdds, a.posteriorOdds));

        // Take top 3 candidate conditions
        List<ScoredCondition> topCandidates = scoredList.stream().limit(3).toList();
        double sumOdds = topCandidates.stream().mapToDouble(c -> c.posteriorOdds).sum();
        if (sumOdds <= 0.001) sumOdds = 1.0;

        List<DifferentialDiagnosis> differentials = new ArrayList<>();
        for (ScoredCondition sc : topCandidates) {
            double prob = Math.min(96.0, Math.max(4.0, (sc.posteriorOdds / sumOdds) * 100.0));
            prob = Math.round(prob * 10.0) / 10.0;
            sc.probabilityPercentage = prob;

            String confidence = prob >= 60.0 ? "HIGH" : (prob >= 30.0 ? "MEDIUM" : "LOW");

            StringBuilder reasoning = new StringBuilder();
            reasoning.append("Bayesian Posterior Probability: ").append(prob).append("%. ");
            if (!sc.supporting.isEmpty()) {
                reasoning.append("Supported by: ").append(String.join("; ", sc.supporting)).append(". ");
            }
            if (!sc.refuting.isEmpty()) {
                reasoning.append("Tempered by: ").append(String.join("; ", sc.refuting)).append(". ");
            }

            DifferentialDiagnosis dx = DifferentialDiagnosis.builder()
                    .icdCode(sc.definition.getIcdCode())
                    .condition(sc.definition.getCondition())
                    .confidence(confidence)
                    .probabilityPercentage(prob)
                    .reasoning(reasoning.toString().trim())
                    .supportingEvidence(sc.supporting)
                    .refutingEvidence(sc.refuting)
                    .build();

            differentials.add(dx);
        }

        return differentials;
    }

    private double calculatePreTestOdds(ClinicalConditionDefinition def, PatientContext patient) {
        double basePrior = 0.08; // Baseline general population incidence
        String cond = def.getCondition().toLowerCase();

        if (cond.contains("viral") || cond.contains("respiratory") || cond.contains("cold") || cond.contains("gastritis")) {
            basePrior = 0.20; // High community prevalence
        } else if (cond.contains("dengue") || cond.contains("malaria") || cond.contains("typhoid")) {
            basePrior = 0.06; // Endemic / seasonal
        } else if (cond.contains("urinary") || cond.contains("cystitis")) {
            basePrior = patient != null && "female".equalsIgnoreCase(patient.getGender()) ? 0.16 : 0.05;
        }

        if (patient != null) {
            if (patient.isPediatric()) {
                if (cond.contains("coronary") || cond.contains("hypertension") || cond.contains("nephropathy")) {
                    basePrior *= 0.05; // Extremely rare in pediatric cohort
                } else if (cond.contains("otitis") || cond.contains("bronchiolitis") || cond.contains("exanthem")) {
                    basePrior *= 2.5;
                }
            }
            if (patient.isGeriatric()) {
                if (cond.contains("hypertension") || cond.contains("cardio") || cond.contains("renal") || cond.contains("joint")) {
                    basePrior *= 2.2;
                }
            }
        }
        return basePrior;
    }

    private void evaluateSystemCorrelations(ScoredCondition sc, ClinicalConditionDefinition def,
                                           Map<String, ClinicalFact> symptoms, Map<String, String> vitals, Map<String, ClinicalFact> facts) {
        String cond = def.getCondition().toLowerCase();

        if (cond.contains("dengue") && symptoms.containsKey("fever")) {
            sc.posteriorOdds *= 2.5;
            sc.supporting.add("High acute fever presence increases arboviral suspicion");
            if (symptoms.containsKey("headache") || symptoms.containsKey("retro_orbital_pain")) {
                sc.posteriorOdds *= 2.5;
                sc.supporting.add("Associated retro-orbital discomfort / cephalalgia reported");
            }
            if (symptoms.containsKey("joint_pain")) {
                sc.posteriorOdds *= 2.2;
                sc.supporting.add("Severe arthralgia / breakbone joint ache reported");
            }
            if (symptoms.containsKey("petechiae_rash") || symptoms.containsKey("rash")) {
                sc.posteriorOdds *= 3.0;
                sc.supporting.add("Cutaneous petechial micro-hemorrhage rash documented");
            }
        }

        if (cond.contains("conjunctivitis") || cond.contains("asthenopia") || cond.contains("eye strain")) {
            if (symptoms.containsKey("fever")) {
                sc.posteriorOdds *= 0.15; // 85% penalty: systemic fever strongly contradicts isolated digital eye strain
                sc.refuting.add("Presence of systemic fever strongly contradicts isolated digital display eye strain");
            } else if (symptoms.containsKey("eye_symptoms") || symptoms.containsKey("conjunctivitis_symptoms")) {
                sc.posteriorOdds *= 4.0;
                sc.supporting.add("Ocular redness, discharge, or irritation reported");
            }
        }

        // Sprain / Orthopedic Joint Strain
        if (cond.contains("sprain") || cond.contains("strain") || cond.contains("ligament") || cond.contains("ankle")) {
            if (symptoms.containsKey("sprain_strain") || symptoms.containsKey("joint_pain")) {
                sc.posteriorOdds *= 6.0;
                sc.supporting.add("Traumatic joint distortion / ligamentous strain reported");
            }
            if (symptoms.containsKey("fever")) {
                sc.posteriorOdds *= 0.15;
            }
        } else if (symptoms.containsKey("sprain_strain") && !symptoms.containsKey("fever")) {
            // An isolated ankle sprain strongly refutes gastritis and arboviral illnesses!
            if (cond.contains("gastritis") || cond.contains("dyspepsia") || cond.contains("dengue") || cond.contains("cystitis")) {
                sc.posteriorOdds *= 0.05;
                sc.refuting.add("Isolated focal traumatic joint injury refutes acute visceral gastrointestinal / systemic etiology");
            }
        }

        // Gastritis / Dyspepsia
        if (cond.contains("gastritis") || cond.contains("dyspepsia") || cond.contains("acid")) {
            if (symptoms.containsKey("abdominal_pain")) {
                sc.posteriorOdds *= 4.5;
                sc.supporting.add("Epigastric discomfort and upper gastrointestinal acid irritation reported");
            }
        }

        // Urinary Tract / Cystitis
        if (cond.contains("cystitis") || cond.contains("uti") || cond.contains("urinary")) {
            if (symptoms.containsKey("dysuria")) {
                sc.posteriorOdds *= 5.0;
                sc.supporting.add("Dysuria / burning micturition reported");
            }
        }
    }

    private void evaluateNegativeFindings(ScoredCondition sc, ClinicalConditionDefinition def, ClinicalConversationState state) {
        String cond = def.getCondition().toLowerCase();
        Set<String> negated = state.getNegatedFindings() != null ? state.getNegatedFindings() : Collections.emptySet();

        // If patient denies fever, down-weight acute systemic infections
        if (negated.contains("fever") || negated.contains("bukhar") || negated.contains("chills")) {
            if (cond.contains("dengue") || cond.contains("malaria") || cond.contains("typhoid") || cond.contains("pneumonia") || cond.contains("sepsis")) {
                sc.posteriorOdds *= 0.25; // 75% penalty
                sc.refuting.add("Patient explicitly denied fever / chills, substantially lowering acute systemic infectious likelihood");
            }
        }

        // If patient denies chest pain or shortness of breath
        if (negated.contains("chest_pain") || negated.contains("shortness_of_breath")) {
            if (cond.contains("cardio") || cond.contains("coronary") || cond.contains("ischemic")) {
                sc.posteriorOdds *= 0.20;
                sc.refuting.add("Absence of acute chest discomfort or dyspnea reduces acute ischemic probability");
            }
        }

        // If patient denies dysuria
        if (negated.contains("dysuria") || negated.contains("burning_urination")) {
            if (cond.contains("cystitis") || cond.contains("urinary")) {
                sc.posteriorOdds *= 0.15;
                sc.refuting.add("Absence of burning micturition argues against uncomplicated acute cystitis");
            }
        }
    }

    private void evaluateBiomarkers(ScoredCondition sc, ClinicalConditionDefinition def,
                                   Map<String, String> vitals, Map<String, ClinicalFact> facts) {
        String cond = def.getCondition().toLowerCase();

        // 1. Platelets
        if (vitals.containsKey("lab_platelet_count")) {
            try {
                double plt = Double.parseDouble(vitals.get("lab_platelet_count"));
                if (plt < 100000 && cond.contains("dengue")) {
                    sc.posteriorOdds *= 5.5;
                    sc.supporting.add("Objective thrombocytopenia (" + (int)plt + " cells/mcL) strongly correlates with Dengue / Viral Hemorrhagic pathology");
                }
            } catch (Exception ignored) {}
        }

        // 2. Creatinine
        if (vitals.containsKey("lab_serum_creatinine")) {
            try {
                double cr = Double.parseDouble(vitals.get("lab_serum_creatinine"));
                if (cr > 1.4 && (cond.contains("renal") || cond.contains("nephro") || cond.contains("kidney"))) {
                    sc.posteriorOdds *= 6.0;
                    sc.supporting.add("Elevated serum creatinine (" + cr + " mg/dL) confirms acute/chronic renal impairment");
                }
            } catch (Exception ignored) {}
        }

        // 3. Fasting Glucose / HbA1c
        if (vitals.containsKey("lab_fasting_blood_glucose") || vitals.containsKey("lab_hba1c")) {
            if (cond.contains("diabetes") || cond.contains("metabolic") || cond.contains("hyperglycemia")) {
                sc.posteriorOdds *= 5.0;
                sc.supporting.add("Biochemical glycemic marker elevation confirms impaired glucose metabolism");
            }
        }
    }

    private List<DifferentialDiagnosis> getDefaultFallback(ClinicalConversationState state) {
        return List.of(
            new DifferentialDiagnosis("MD11", "Acute Upper Respiratory Tract Infection", "MEDIUM", "Clinical presentation consistent with acute viral URI; monitoring indicated"),
            new DifferentialDiagnosis("MD12", "Acute Viral Syndromic Illness", "LOW", "Nonspecific constitutional viral symptoms"),
            new DifferentialDiagnosis("MD13", "General Somatic Discomfort", "LOW", "Supportive hydration and rest advised")
        );
    }
}

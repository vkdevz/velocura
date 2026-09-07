package com.velocura.ai.clinical.safety;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.ai.clinical.state.PatientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic Safety Kernel:
 * Authoritative, non-overrideable safety decision engine.
 * The LLM CANNOT override safety kernel BLOCKS or ESCALATIONS.
 */
@Component
public class DeterministicSafetyKernel {

    private static final Logger log = LoggerFactory.getLogger(DeterministicSafetyKernel.class);

    // Adversarial prompt injection patterns
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
        "(?i)\\b(ignore\\s+(all\\s+)?(previous|prior|clinical)?\\s*(safety|rules|instructions|guidelines)?|pretend\\s+(to\\s+be|there\\s+are\\s+no\\s+allergies)|override\\s+(the\\s+)?safety|bypass\\s+(the\\s+)?rules|output\\s+(a\\s+)?prescription\\s+directly|disregard\\s+safety|jailbreak|system\\s+prompt|\\bdan\\b|unrestricted\\s+(ai|doctor|physician|assistant))\\b"
    );

    // Autonomous prescription prevention pattern
    private static final Pattern PRESCRIPTION_CLAIM_PATTERN = Pattern.compile(
        "(?i)\\b(i\\s+(will\\s+)?(write|issue|give)\\s+(you\\s+)?(a\\s+)?prescription|here\\s+is\\s+your\\s+prescription|i\\s+prescribe\\b|prescribing\\s+\\d+\\s*(mg|g|ml)|write\\s+you\\s+a\\s+prescription)\\b"
    );

    // Medication detection patterns for safety checking
    private static final Pattern ASPIRIN_PATTERN = Pattern.compile("(?i)\\b(aspirin|acetylsalicylic|ecotrin|disprin)\\b");
    private static final Pattern NSAID_PATTERN = Pattern.compile("(?i)\\b(ibuprofen|advil|motrin|naproxen|aleve|diclofenac|voltaren|indomethacin|ketorolac)\\b");
    private static final Pattern PENICILLIN_PATTERN = Pattern.compile("(?i)\\b(penicillin|amoxicillin|augmentin|ampicillin|piperacillin)\\b");

    public enum SafetyAction {
        ALLOW,
        MODIFY,
        BLOCK,
        ESCALATE
    }

    public static class SafetyDecision {
        private final SafetyAction action;
        private final List<String> reasons;
        private final String finalMessage;

        public SafetyDecision(SafetyAction action, List<String> reasons, String finalMessage) {
            this.action = action;
            this.reasons = reasons != null ? reasons : new ArrayList<>();
            this.finalMessage = finalMessage;
        }

        public static SafetyDecision allow(String message) {
            return new SafetyDecision(SafetyAction.ALLOW, List.of(), message);
        }

        public static SafetyDecision block(String reason, String safeMessage) {
            return new SafetyDecision(SafetyAction.BLOCK, List.of(reason), safeMessage);
        }

        public static SafetyDecision escalate(String trigger, String emergencyMessage) {
            return new SafetyDecision(SafetyAction.ESCALATE, List.of(trigger), emergencyMessage);
        }

        public static SafetyDecision modify(List<String> reasons, String modifiedMessage) {
            return new SafetyDecision(SafetyAction.MODIFY, reasons, modifiedMessage);
        }

        public SafetyAction getAction() { return action; }
        public List<String> getReasons() { return reasons; }
        public String getFinalMessage() { return finalMessage; }
        public boolean isBlocked() { return action == SafetyAction.BLOCK; }
        public boolean isEscalate() { return action == SafetyAction.ESCALATE; }
    }

    /**
     * Checks user input for adversarial prompt injections attempting to bypass safety rules.
     */
    public boolean isPromptInjection(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) return false;
        return PROMPT_INJECTION_PATTERN.matcher(rawInput).find();
    }

    /**
     * Authoritative evaluation of proposed clinical message and state.
     */
    public SafetyDecision evaluate(String proposedMessage, ClinicalConversationState state, String rawInput) {
        // 1. Check prompt injection attempt
        if (isPromptInjection(rawInput)) {
            log.warn("[SAFETY KERNEL] Prompt injection detected. Enforcing safety block.");
            return SafetyDecision.block(
                "Prompt injection / safety override attempt detected",
                "I cannot bypass medical safety rules or clinical guidelines. " +
                "Please describe your symptoms or clinical question plainly so I can provide safe, objective guidance."
            );
        }

        // 2. Critical Emergency Check
        if (state != null && state.getCurrentRiskLevel() == ClinicalRiskLevel.CRITICAL) {
            log.warn("[SAFETY KERNEL] State is CRITICAL. Enforcing emergency escalation.");
            return SafetyDecision.escalate(
                "Immediate life threat or red flag present in clinical state",
                "URGENT MEDICAL NOTICE: Your symptoms indicate a potentially serious emergency. " +
                "Please call emergency services (108 / 911) or proceed immediately to the nearest Emergency Department. " +
                "Do not wait for symptoms to resolve on their own."
            );
        }

        if (proposedMessage == null || proposedMessage.isBlank()) {
            return SafetyDecision.allow("Please share more about how you are feeling so I can advise you safely.");
        }

        // Check for unauthorized autonomous prescription issuance
        if (PRESCRIPTION_CLAIM_PATTERN.matcher(proposedMessage).find()) {
            log.warn("[SAFETY KERNEL] Autonomous prescription attempt detected. Enforcing prescription restriction block.");
            return SafetyDecision.block(
                "PRESCRIPTION_RESTRICTION: AI cannot write medical prescriptions",
                "As an AI clinical guidance assistant, I cannot write prescriptions or prescribe medications. Only a licensed physician can diagnose medical conditions and issue prescriptions. Please consult your physician or book a doctor consultation for medication needs."
            );
        }

        List<String> safetyModifications = new ArrayList<>();
        String currentMessage = proposedMessage;

        // 3. Deterministic Allergy Conflict Checking
        if (state != null && state.getAllergies() != null && !state.getAllergies().isEmpty()) {
            for (String allergy : state.getAllergies()) {
                String a = allergy.toLowerCase(Locale.ROOT);
                if ((a.contains("nsaid") || a.contains("ibuprofen") || a.contains("aspirin")) && NSAID_PATTERN.matcher(currentMessage).find()) {
                    log.warn("[SAFETY KERNEL] Allergy conflict detected: NSAID allergy vs proposed NSAID recommendation");
                    safetyModifications.add("Patient has documented NSAID allergy; removed contraindicated anti-inflammatory suggestion.");
                    currentMessage = currentMessage.replaceAll("(?i)\\b(ibuprofen|advil|motrin|naproxen|aleve|diclofenac)\\b", "[CONTRAINDICATED DUE TO ALLERGY]");
                    currentMessage += "\n\n⚠️ SAFETY NOTICE: Non-steroidal anti-inflammatory drugs (NSAIDs) were suppressed from this recommendation due to your recorded allergy.";
                }
                if (a.contains("penicillin") && PENICILLIN_PATTERN.matcher(currentMessage).find()) {
                    log.warn("[SAFETY KERNEL] Allergy conflict detected: Penicillin allergy vs proposed penicillin derivative");
                    safetyModifications.add("Patient has documented Penicillin allergy; blocked penicillin suggestion.");
                    currentMessage = currentMessage.replaceAll("(?i)\\b(penicillin|amoxicillin|augmentin|ampicillin)\\b", "[CONTRAINDICATED DUE TO ALLERGY]");
                    currentMessage += "\n\n⚠️ SAFETY NOTICE: Penicillin-class antibiotics were blocked due to your documented penicillin allergy.";
                }
            }
        }

        // 4. Age-Related Pediatric Restrictions (e.g. Aspirin in pediatric viral illness -> Reye's Syndrome)
        if (state != null && state.getPatientContext() != null) {
            PatientContext ctx = state.getPatientContext();
            boolean isChildOrTeen = ctx.isPediatric() || (ctx.getAgeYears() != null && ctx.getAgeYears() < 19.0);

            if (isChildOrTeen && ASPIRIN_PATTERN.matcher(currentMessage).find()) {
                log.warn("[SAFETY KERNEL] Pediatric Aspirin contraindication detected (Reye's syndrome risk)");
                safetyModifications.add("Aspirin is strictly contraindicated in children and teenagers due to Reye's syndrome risk.");
                currentMessage = ASPIRIN_PATTERN.matcher(currentMessage).replaceAll("[CONTRAINDICATED IN CHILDREN: RISK OF REYE'S SYNDROME]");
                currentMessage += "\n\n⚠️ PEDIATRIC WARNING: Aspirin must NEVER be given to children or teenagers due to the risk of fatal Reye's syndrome. Use pediatric acetaminophen or ibuprofen only under pediatrician guidance.";
            }
        }

        // 5. Medical History Contraindications (e.g. NSAIDs in active GI bleeding / Peptic Ulcer)
        if (state != null && state.getMedicalHistory() != null) {
            for (String hist : state.getMedicalHistory()) {
                String h = hist.toLowerCase(Locale.ROOT);
                if ((h.contains("ulcer") || h.contains("gi bleed") || h.contains("kidney disease") || h.contains("renal failure"))
                        && NSAID_PATTERN.matcher(currentMessage).find()) {
                    log.warn("[SAFETY KERNEL] NSAID contraindication detected against medical history: {}", hist);
                    safetyModifications.add("NSAIDs contraindicated in patients with history of " + hist);
                    currentMessage = NSAID_PATTERN.matcher(currentMessage).replaceAll("[CONTRAINDICATED DUE TO " + hist.toUpperCase() + "]");
                    currentMessage += "\n\n⚠️ CONTRAINDICATION: NSAIDs (like Ibuprofen/Naproxen) are contraindicated with your history of " + hist + ".";
                }
            }
        }

        if (!safetyModifications.isEmpty()) {
            return SafetyDecision.modify(safetyModifications, currentMessage);
        }

        return SafetyDecision.allow(currentMessage);
    }

    public SafetyDecision validateMedicationSafety(com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment assessment) {
        if (assessment == null) {
            return SafetyDecision.allow("No medication safety assessment available.");
        }
        if (assessment.isRequiresImmediateEscalation() || assessment.getOverallSafetyStatus() == com.velocura.ai.clinical.medication.model.MedicationSafetyStatus.CRITICAL) {
            log.warn("[SAFETY KERNEL] Critical medication safety issue identified. Blocking unsafe advice and escalating.");
            return SafetyDecision.block(
                "CRITICAL_MEDICATION_SAFETY_ALERT: " + String.join("; ", assessment.getCriticalWarnings()),
                assessment.getPatientFacingGuidance()
            );
        }
        return SafetyDecision.allow(assessment.getPatientFacingGuidance());
    }
}

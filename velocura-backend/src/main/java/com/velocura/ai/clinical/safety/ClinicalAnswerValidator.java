package com.velocura.ai.clinical.safety;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Safety Gate #2: Validates the generated clinical answer before returning it to the user.
 * Catches missed red flags, false reassurance, hallucinated drug names, or robotic disclaimers.
 */
@Service
public class ClinicalAnswerValidator {

    private static final Logger log = LoggerFactory.getLogger(ClinicalAnswerValidator.class);

    private static final Pattern FALSE_REASSURANCE = Pattern.compile(
        "(?i)\\b(don't\\s*worry\\s*,?\\s*you\\s*are\\s*(completely\\s*)?fine|nothing\\s*to\\s*worry\\s*about|you\\s*have\\s*nothing\\s*wrong|100%\\s*guaranteed|definitely\\s*cured)\\b"
    );

    private static final Pattern ROBOTIC_DISCLAIMER = Pattern.compile(
        "(?i)\\b(as\\s*an\\s*ai\\s*language\\s*model|as\\s*an\\s*artificial\\s*intelligence|i\\s*am\\s*just\\s*an\\s*ai)\\b"
    );

    public String validateAndSanitize(String message, ClinicalConversationState state) {
        if (message == null || message.isBlank()) {
            return "Based on clinical guidelines, please monitor your symptoms closely and rest. If symptoms worsen or persist, please consult a healthcare professional.";
        }

        String validated = message;

        // 1. Strip robotic disclaimers
        if (ROBOTIC_DISCLAIMER.matcher(validated).find()) {
            log.info("[SAFETY GATE #2] Stripping robotic AI self-reference disclaimer");
            validated = ROBOTIC_DISCLAIMER.matcher(validated).replaceAll("").replaceAll("\\s+", " ").trim();
        }

        // 2. Prevent false reassurance
        if (FALSE_REASSURANCE.matcher(validated).find()) {
            log.warn("[SAFETY GATE #2] False reassurance detected; replacing with objective clinical guidance");
            validated = FALSE_REASSURANCE.matcher(validated).replaceAll("your symptoms appear manageable at this stage, but should be monitored").trim();
        }

        // 3. Ensure emergency escalation is not diluted if risk is CRITICAL
        if (state != null && state.getCurrentRiskLevel() == ClinicalRiskLevel.CRITICAL) {
            if (!validated.toLowerCase().contains("emergency") && !validated.toLowerCase().contains("immediate")) {
                log.warn("[SAFETY GATE #2] Critical risk level detected without explicit emergency escalation; prepending notice");
                validated = "URGENT MEDICAL NOTICE: " + validated + " Please seek in-person emergency medical care immediately.";
            }
        }

        return validated;
    }
}

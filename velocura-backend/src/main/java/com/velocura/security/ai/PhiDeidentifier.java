package com.velocura.security.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Healthcare-grade PHI (Protected Health Information) De-identifier and AI Guardrail.
 * Scrubs identifiable personal markers before payloads reach external LLM endpoints
 * and filters out malicious prompt injection vectors.
 */
@Component
public class PhiDeidentifier {

    private static final Logger logger = LoggerFactory.getLogger(PhiDeidentifier.class);

    // Regex patterns for PHI detection
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}");
    private static final Pattern SSN_OR_NATIONAL_ID = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{4}[-\\s]\\d{4}[-\\s]\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b");

    // Prompt injection heuristic patterns
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions|system\\s+prompt|act\\s+as\\s+(dan|an\\s+unrestricted|root)|disregard\\s+all|override\\s+system|jailbreak)"
    );

    /**
     * Sanitizes medical query input: scrubs PII/PHI and defuses prompt injection.
     *
     * @param rawText raw input from patient or clinician
     * @return de-identified, sanitized string safe for LLM context
     */
    public String sanitizeForAi(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String cleaned = rawText;

        // 1. Guard against prompt injection
        if (PROMPT_INJECTION_PATTERN.matcher(cleaned).find()) {
            logger.warn("Prompt injection signature detected and neutralized in input: {}", cleaned);
            cleaned = PROMPT_INJECTION_PATTERN.matcher(cleaned).replaceAll("[SAFETY_FLAG: INJECTION_NEUTRALIZED]");
        }

        // 2. Redact sensitive identifiers (PHI)
        cleaned = EMAIL_PATTERN.matcher(cleaned).replaceAll("[REDACTED_EMAIL]");
        cleaned = PHONE_PATTERN.matcher(cleaned).replaceAll("[REDACTED_PHONE]");
        cleaned = SSN_OR_NATIONAL_ID.matcher(cleaned).replaceAll("[REDACTED_NATIONAL_ID]");
        cleaned = CREDIT_CARD_PATTERN.matcher(cleaned).replaceAll("[REDACTED_FINANCIAL_INFO]");

        return cleaned;
    }

    /**
     * Checks if text contains potential prompt injection attempt.
     */
    public boolean containsPromptInjection(String rawText) {
        if (rawText == null) return false;
        return PROMPT_INJECTION_PATTERN.matcher(rawText).find();
    }
}

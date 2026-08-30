package com.velocura.phi;

import org.springframework.stereotype.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HIPAA/DPDP-aligned PHI de-identification and AI Safety Guardrails.
 * Applied to ALL user input before transmission to any external AI API.
 */
@Component
public class PhiDeidentifier {

    private static final Pattern SSN     = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern AADHAAR = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern PAN     = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");
    private static final Pattern EMAIL   = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE   = Pattern.compile("(?:\\+?1[\\s-]?)?(?:\\(?\\d{3}\\)?[\\s-]?)?\\d{3}[\\s-]\\d{4}|(?:\\+?91[\\s-]?)?\\b\\d{10}\\b");
    private static final Pattern DOB     = Pattern.compile(
        "(?i)\\b(?:dob|born(?:\\s+on)?|d\\.o\\.b\\.?)\\s*[:\\-]?\\s*\\d{1,2}[/\\-.\\s]\\d{1,2}[/\\-.\\s]\\d{2,4}\\b");
    private static final Pattern NAME_HINT = Pattern.compile(
        "(?i)\\b(?:my name is|name\\s*:)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,2})");

    private static final Pattern PROMPT_INJECTION = Pattern.compile(
        "(?i)(ignore (all )?previous instructions|disregard previous|system prompt|system instruction|leak api key|developer message|reveal instructions|jailbreak|bypass safety|database keys|override system)"
    );

    public boolean containsPromptInjection(String input) {
        if (input == null || input.isBlank()) return false;
        return PROMPT_INJECTION.matcher(input).find();
    }

    public String sanitize(String input) {
        if (input == null || input.isBlank()) return "";
        String s = SSN.matcher(input).replaceAll("[REDACTED_NATIONAL_ID]");
        s = AADHAAR.matcher(s).replaceAll("[REDACTED_NATIONAL_ID]");
        s = PAN.matcher(s).replaceAll("[REDACTED_NATIONAL_ID]");
        s = EMAIL.matcher(s).replaceAll("[REDACTED_EMAIL]");
        s = PHONE.matcher(s).replaceAll("[REDACTED_PHONE]");
        s = DOB.matcher(s).replaceAll("[REDACTED_DOB]");
        Matcher m = NAME_HINT.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                m.group(0).replace(m.group(1), "[REDACTED_NAME]")));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    public String sanitizeForAi(String input) {
        if (input == null || input.isBlank()) return "";
        if (containsPromptInjection(input)) {
            String sanitized = PROMPT_INJECTION.matcher(input).replaceAll("[SAFETY_FLAG: INJECTION_NEUTRALIZED]");
            return sanitize(sanitized);
        }
        return sanitize(input);
    }
}

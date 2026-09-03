package com.velocura.ai.clinical.safety;

import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.ai.clinical.state.PatientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Safety Gate #1: Deterministic red-flag and life-threat screening engine.
 * Runs on every conversational turn before language model processing.
 */
@Service
public class SafetyScreeningEngine {

    private static final Logger log = LoggerFactory.getLogger(SafetyScreeningEngine.class);

    // ─── Emergency Clinical Patterns ──────────────────────────────────────────
    private static final Pattern CHEST_EMERGENCY = Pattern.compile(
        "(?i)\\b(chest\\s*(pain|pressure|tightness|heaviness|squeezing)|radiat(ing|es)?\\s*to\\s*(arm|jaw|neck|back)|crushing\\s*chest|angina|heart\\s*attack|myocardial|retrosternal\\s*(pain|pressure))\\b"
    );

    private static final Pattern RESPIRATORY_EMERGENCY = Pattern.compile(
        "(?i)\\b(can't\\s*breathe|cannot\\s*breathe|struggling\\s*to\\s*breathe|severe\\s*(shortness\\s*of\\s*breath|breathlessness|dyspnea)|suffocating|gasping\\s*for\\s*air|blue\\s*lips|cyanosis|lips\\s*turning\\s*blue|stridor)\\b"
    );

    private static final Pattern NEURO_STROKE_EMERGENCY = Pattern.compile(
        "(?i)\\b(stroke|facial\\s*droop|face\\s*droop|arm\\s*weakness|slurred\\s*speech|can't\\s*speak|cannot\\s*speak|sudden\\s*numbness\\s*on\\s*one\\s*side|sudden\\s*paralysis|sudden\\s*loss\\s*of\\s*vision|fast\\s*symptoms)\\b"
    );

    private static final Pattern CONSCIOUSNESS_SEIZURE = Pattern.compile(
        "(?i)\\b(unconscious|passed\\s*out|blacked\\s*out|fainted|loss\\s*of\\s*consciousness|unresponsive|seizure|convulsion|having\\s*a\\s*fit|epileptic\\s*fit)\\b"
    );

    private static final Pattern OVERDOSE_POISONING = Pattern.compile(
        "(?i)\\b(overdose|took\\s*\\d+\\s*(tablets|pills|capsules|doses)|drank\\s*(poison|bleach|chemical|detergent|acid)|swallowed\\s*(poison|battery|chemical)|accidental\\s*ingestion|paracetamol\\s*overdose|toxic\\s*ingestion)\\b"
    );

    private static final Pattern ANAPHYLAXIS = Pattern.compile(
        "(?i)\\b(anaphylaxis|throat\\s*closing|throat\\s*swelling|tongue\\s*swelling|swollen\\s*(lips|tongue|throat)\\s*after|allergic\\s*reaction.*(breathe|swallow|throat)|can't\\s*swallow.*allergic)\\b"
    );

    private static final Pattern BLEEDING_EMERGENCY = Pattern.compile(
        "(?i)\\b(uncontrolled\\s*bleeding|profuse\\s*bleeding|bleeding\\s*won't\\s*stop|spurting\\s*blood|vomiting\\s*blood|coughing\\s*up\\s*(blood|large\\s*clots)|large\\s*amount\\s*of\\s*blood)\\b"
    );

    private static final Pattern SUICIDAL_SELF_HARM = Pattern.compile(
        "(?i)\\b(suicide|suicidal|want\\s*to\\s*kill\\s*myself|end\\s*my\\s*life|going\\s*to\\s*hang|cut\\s*my\\s*wrists|better\\s*off\\s*dead|don't\\s*want\\s*to\\s*live)\\b"
    );

    private static final Pattern MENINGITIS_SIGNS = Pattern.compile(
        "(?i)\\b(stiff\\s*neck.*(fever|high\\s*temp)|fever.*(stiff\\s*neck|neck\\s*rigidity|photophobia|rash\\s*won't\\s*fade|glass\\s*test))\\b"
    );

    /**
     * Evaluates user input and current patient context for life-threatening conditions.
     */
    public SafetyScreeningResult screen(String message, PatientContext patientContext) {
        if (message == null || message.isBlank()) {
            return SafetyScreeningResult.safe();
        }

        String text = message.trim();
        List<String> redFlags = new ArrayList<>();
        String reason = null;

        // 1. Suicide / Self-harm
        if (SUICIDAL_SELF_HARM.matcher(text).find()) {
            redFlags.add("Acute suicide or self-harm emergency signal detected");
            return buildEmergencyResponse(
                "Immediate self-harm risk detected.",
                "If you or someone you know is in immediate danger or thinking about self-harm, please reach out for immediate help right now. "
                    + getEmergencyContactInstruction(patientContext, true),
                redFlags
            );
        }

        // 2. Overdose / Poisoning
        if (OVERDOSE_POISONING.matcher(text).find()) {
            redFlags.add("Acute medication overdose or poisoning suspected");
            return buildEmergencyResponse(
                "Acute medication overdose or toxic ingestion.",
                "This requires immediate medical attention. Do not try to induce vomiting unless directed by poison control. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 3. Chest Pain / Cardiac Emergency
        if (CHEST_EMERGENCY.matcher(text).find()) {
            redFlags.add("Possible acute coronary syndrome or myocardial ischemia");
            redFlags.add("Chest pain/pressure with potential radiation");
            return buildEmergencyResponse(
                "Possible acute cardiac event or angina.",
                "Sit comfortably, rest quietly to minimize cardiac strain, and seek urgent medical care. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 4. Severe Respiratory Distress
        if (RESPIRATORY_EMERGENCY.matcher(text).find()) {
            redFlags.add("Severe respiratory compromise or cyanosis");
            return buildEmergencyResponse(
                "Severe respiratory distress.",
                "Sit upright in a well-ventilated area, remain as calm as possible, and call for emergency medical assistance immediately. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 5. Stroke / Acute Neurological Signs
        if (NEURO_STROKE_EMERGENCY.matcher(text).find()) {
            redFlags.add("Acute focal neurological deficits (FAST stroke criteria)");
            return buildEmergencyResponse(
                "Possible acute stroke or neurological emergency.",
                "Time is critical with stroke symptoms. Note the exact time symptoms started and seek emergency medical care immediately. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 6. Loss of Consciousness / Seizure
        if (CONSCIOUSNESS_SEIZURE.matcher(text).find()) {
            redFlags.add("Loss of consciousness, syncope, or active seizure");
            return buildEmergencyResponse(
                "Loss of consciousness or acute seizure activity.",
                "Place the person in a safe recovery position on their side away from sharp objects. Do not place anything in their mouth. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 7. Anaphylaxis
        if (ANAPHYLAXIS.matcher(text).find()) {
            redFlags.add("Severe systemic allergic reaction / airway compromise");
            return buildEmergencyResponse(
                "Suspected anaphylactic reaction.",
                "If an epinephrine auto-injector (EpiPen) is available, administer it as prescribed and seek emergency assistance immediately. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 8. Severe Uncontrolled Bleeding
        if (BLEEDING_EMERGENCY.matcher(text).find()) {
            redFlags.add("Severe or uncontrolled hemorrhage");
            return buildEmergencyResponse(
                "Severe uncontrolled bleeding.",
                "Apply firm, continuous pressure with a clean cloth directly over the bleeding site and keep the injured area elevated if possible. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 9. Meningitis signs
        if (MENINGITIS_SIGNS.matcher(text).find()) {
            redFlags.add("Fever combined with neck stiffness (meningeal irritation signs)");
            return buildEmergencyResponse(
                "Potential central nervous system infection (meningitis).",
                "Fever accompanied by a stiff neck requires urgent medical evaluation at an emergency center. "
                    + getEmergencyContactInstruction(patientContext, false),
                redFlags
            );
        }

        // 10. High-Risk Pediatric Presentation (e.g. Infant < 3 months with fever)
        if (patientContext != null && (patientContext.isInfant() || (patientContext.getAgeMonths() != null && patientContext.getAgeMonths() <= 3))) {
            String lower = text.toLowerCase();
            if (lower.contains("fever") || lower.contains("temp") || lower.contains("10") || lower.contains("bukhar")) {
                redFlags.add("Pediatric neonate/infant (under 3 months) presenting with fever");
                return buildEmergencyResponse(
                    "Fever in a young infant (under 3 months).",
                    "Fever in infants under 3 months is a clinical red flag requiring prompt in-person pediatric emergency evaluation. "
                        + getEmergencyContactInstruction(patientContext, false),
                    redFlags
                );
            }
        }

        // 11. Pregnancy Emergencies (Severe abdominal pain, bleeding during pregnancy)
        if (patientContext != null && patientContext.getPregnancyStatus() == PatientContext.PregnancyStatus.PREGNANT) {
            String lower = text.toLowerCase();
            if (lower.contains("bleed") || lower.contains("severe pain") || lower.contains("cramp") || lower.contains("fluid leak")) {
                redFlags.add("Pregnancy with acute bleeding or severe abdominal pain");
                return buildEmergencyResponse(
                    "Acute symptom during pregnancy.",
                    "Severe abdominal pain or vaginal bleeding during pregnancy requires urgent obstetric emergency assessment. "
                        + getEmergencyContactInstruction(patientContext, false),
                    redFlags
                );
            }
        }

        return SafetyScreeningResult.safe();
    }

    private SafetyScreeningResult buildEmergencyResponse(String reason, String advice, List<String> redFlags) {
        log.warn("[SAFETY GATE #1 ALERT] Emergency condition detected: {}. RedFlags: {}", reason, redFlags);
        return SafetyScreeningResult.builder()
                .isEmergency(true)
                .riskLevel(ClinicalRiskLevel.CRITICAL)
                .emergencyReason(reason)
                .emergencyAdvice(advice)
                .redFlags(redFlags)
                .build();
    }

    /**
     * Determines the appropriate emergency contact instruction based on country/location context.
     * Never displays an Indian number to an unknown country, nor a US number to an unknown country.
     */
    public String getEmergencyContactInstruction(PatientContext context, boolean isMentalHealth) {
        String country = context != null ? context.getCountryLocation() : null;

        if (isMentalHealth) {
            if ("IN".equalsIgnoreCase(country)) {
                return "Call 108 / 112, or reach the Tele-MANAS helpline at 14416.";
            } else if ("US".equalsIgnoreCase(country)) {
                return "Call or text 988 (Suicide & Crisis Lifeline), or call 911 immediately.";
            } else {
                return "Contact your local emergency service (such as 108, 911, or 112) or call your national crisis support line immediately.";
            }
        }

        if ("IN".equalsIgnoreCase(country)) {
            return "Call 108 or 112 immediately for emergency medical care.";
        } else if ("US".equalsIgnoreCase(country)) {
            return "Call 911 immediately or go to the nearest emergency room.";
        } else {
            return "Contact your local emergency service or proceed to the nearest emergency department immediately.";
        }
    }
}

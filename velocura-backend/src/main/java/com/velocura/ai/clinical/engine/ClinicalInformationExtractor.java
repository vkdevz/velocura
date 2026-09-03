package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalFact;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts symptoms, timeline, severity, vitals, medications, and user hypotheses from input
 * and updates the structured clinical state with explicit epistemic status.
 */
@Component
public class ClinicalInformationExtractor {

    private static final Pattern TIMELINE = Pattern.compile(
        "(?i)\\b(started\\s*(?:today|yesterday|recently|just\\s*now)|since\\s*(?:yesterday|today|last\\s*night|morning)|for\\s*\\d+\\s*(?:days?|hours?|weeks?)|\\d+\\s*(?:days?|hours?|weeks?|mins?|minutes?)|two\\s*days\\s*ago|past\\s*\\d+.*days?|1-2\\s*days|1–2\\s*days|1-3\\s*days|1–3\\s*days|3-5\\s*days|3–5\\s*days|more\\s*than\\s*a\\s*week|more\\s*than\\s*\\d+\\s*weeks?)\\b"
    );

    private static final Pattern PROGRESSION = Pattern.compile(
        "(?i)\\b(constant|throughout\\s*the\\s*day|comes\\s*and\\s*goes|in\\s*waves|waves|intermittent|occasional|continuous|getting\\s*worse|worsening|getting\\s*better|improving|after\\s*meals|after\\s*eating|on\\s*exertion|at\\s*night|morning|blurry\\s*vision|eye\\s*strain|redness|burning\\s*while\\s*urinating|frequent\\s*urge|painful\\s*swallowing|throbbing|dull\\s*pressure|bleeding|spurting|blistering|can\\s*bear\\s*weight|unable\\s*to\\s*bear|pop|triggered\\s*by\\s*hot|continuous\\s*throbbing)\\b"
    );

    private static final Pattern SEVERITY = Pattern.compile(
        "(?i)\\b(mild|moderate|severe|critical|very\\s*severe|unbearable|manageable|uncomfortable|hard\\s*to\\s*bear|sharp|dull|throbbing|crushing|\\d+\\s*/\\s*10|\\d+\\s*-\\s*\\d+\\s*/\\s*10|\\d+\\s*–\\s*\\d+\\s*/\\s*10)\\b"
    );

    private static final Pattern HYPOTHESIS = Pattern.compile(
        "(?i)\\b(?:i\\s*(?:think|believe|guess)|might\\s*have|could\\s*be|maybe\\s*it's)\\s*(dengue|malaria|typhoid|covid|pneumonia|flu|jaundice|migraine)\\b"
    );

    private static final Pattern MEDICATIONS = Pattern.compile(
        "(?i)\\b(paracetamol|acetaminophen|amoxicillin|ibuprofen|aspirin|metformin|azithromycin|cetirizine|cough\\s*syrup|antibiotic|painkiller)\\b"
    );

    private static final Pattern ANATOMICAL_SITES = Pattern.compile(
        "(?i)\\b(finger|thumb|toe|hand|foot|feet|palm|wrist|arm|forearm|elbow|shoulder|leg|knee|ankle|calf|thigh|chest|back|neck|head|scalp|face|cheek|chin|lip|jaw|tooth|teeth|gum|eye|eyes|ear|ears|nose|throat|stomach|abdomen|belly|pelvis|groin)\\b"
    );

    private boolean isNegated(String text, String keyword) {
        if (text == null || keyword == null) return false;
        Pattern p = Pattern.compile("(?i)\\b(no|not|without|denies|never|negative\\s+for)\\s+(?:\\w+\\s+){0,3}" + Pattern.quote(keyword) + "\\b");
        return p.matcher(text).find();
    }

    public void extractAndUpdate(String normalizedText, ClinicalConversationState state) {
        if (normalizedText == null || state == null) return;
        String text = normalizedText.toLowerCase();
        int turn = state.getTurnCount();

        // 1. Timeline
        Matcher timeMatcher = TIMELINE.matcher(text);
        if (timeMatcher.find()) {
            String val = timeMatcher.group(1).trim();
            state.getTimeline().put("duration", val);
            state.addFact("duration", ClinicalFact.userReported("duration", val, turn));
        }

        Matcher progMatcher = PROGRESSION.matcher(text);
        if (progMatcher.find()) {
            String val = progMatcher.group(1).trim();
            state.getTimeline().put("progression", val);
            state.addFact("progression", ClinicalFact.userReported("progression", val, turn));
        }

        // 2. Severity
        Matcher sevMatcher = SEVERITY.matcher(text);
        if (sevMatcher.find()) {
            String val = sevMatcher.group(1).trim();
            state.setSeverity(val);
            state.addFact("severity", ClinicalFact.userReported("severity", val, turn));
        }

        // 3. User Hypotheses (Must be marked as USER_REPORTED, never confirmed diagnosis)
        Matcher hypMatcher = HYPOTHESIS.matcher(text);
        if (hypMatcher.find()) {
            String hyp = hypMatcher.group(1).trim();
            if (!state.getUserHypotheses().contains(hyp)) {
                state.getUserHypotheses().add(hyp);
            }
            state.addFact("userHypothesis", ClinicalFact.userReported("userHypothesis", hyp, turn));
        }

        // Extract anatomical site if mentioned
        Matcher siteMatcher = ANATOMICAL_SITES.matcher(text);
        if (siteMatcher.find()) {
            String site = siteMatcher.group(1).toLowerCase();
            state.getTimeline().put("anatomical_site", site);
            state.addFact("anatomical_site", ClinicalFact.userReported("anatomical_site", site, turn));
        }

        // 4. Symptoms Extraction (with Negation Handling)
        if (text.contains("fever")) {
            if (isNegated(text, "fever")) {
                state.addFact("fever", ClinicalFact.userReported("fever", "absent", turn));
            } else {
                state.getSymptoms().put("fever", ClinicalFact.userReported("fever", "present", turn));
                state.addFact("fever", ClinicalFact.userReported("fever", "present", turn));
            }
        }
        if (text.contains("cough")) {
            if (isNegated(text, "cough")) {
                state.addFact("cough", ClinicalFact.userReported("cough", "absent", turn));
            } else {
                String coughType = text.contains("dry") ? "dry" : (text.contains("phlegm") || text.contains("mucus") || text.contains("wet") || text.contains("productive")) ? "productive" : "unspecified";
                state.getSymptoms().put("cough", ClinicalFact.userReported("cough", coughType, turn));
                state.addFact("cough", ClinicalFact.userReported("cough", coughType, turn));
            }
        }
        if (text.contains("headache") || text.contains("head pain") || text.contains("migraine") || text.contains("sar dard") || text.contains("sir dard")) {
            if (isNegated(text, "headache") || isNegated(text, "migraine")) {
                state.addFact("headache", ClinicalFact.userReported("headache", "absent", turn));
            } else {
                state.getSymptoms().put("headache", ClinicalFact.userReported("headache", "present", turn));
                state.addFact("headache", ClinicalFact.userReported("headache", "present", turn));
            }
        }
        if (text.contains("stomach") || text.contains("abdomen") || text.contains("abdominal") || text.contains("belly") || text.contains("tummy") || text.contains("pet dard") || text.contains("cramps")) {
            if (isNegated(text, "pain") || isNegated(text, "cramp")) {
                state.addFact("abdominal_pain", ClinicalFact.userReported("abdominal_pain", "absent", turn));
            } else {
                state.getSymptoms().put("abdominal_pain", ClinicalFact.userReported("abdominal_pain", "present", turn));
                state.addFact("abdominal_pain", ClinicalFact.userReported("abdominal_pain", "present", turn));
            }
        }
        if (text.contains("throat") || text.contains("gala") || text.contains("pharyngitis") || text.contains("tonsil")) {
            if (isNegated(text, "throat") || isNegated(text, "sore throat")) {
                state.addFact("sore_throat", ClinicalFact.userReported("sore_throat", "absent", turn));
            } else {
                state.getSymptoms().put("sore_throat", ClinicalFact.userReported("sore_throat", "present", turn));
                state.addFact("sore_throat", ClinicalFact.userReported("sore_throat", "present", turn));
            }
        }
        if (text.contains("rash") || text.contains("itch") || text.contains("hives") || text.contains("allergy") || text.contains("khujli")) {
            if (isNegated(text, "rash") || isNegated(text, "itch") || isNegated(text, "hives")) {
                state.addFact("rash", ClinicalFact.userReported("rash", "absent", turn));
            } else {
                state.getSymptoms().put("rash", ClinicalFact.userReported("rash", "present", turn));
                state.addFact("rash", ClinicalFact.userReported("rash", "present", turn));
            }
        }
        if (text.contains("diarrhea") || text.contains("loose motion") || text.contains("watery stool") || text.contains("dast")) {
            if (isNegated(text, "diarrhea") || isNegated(text, "loose motion")) {
                state.addFact("diarrhea", ClinicalFact.userReported("diarrhea", "absent", turn));
            } else {
                state.getSymptoms().put("diarrhea", ClinicalFact.userReported("diarrhea", "present", turn));
                state.addFact("diarrhea", ClinicalFact.userReported("diarrhea", "present", turn));
            }
        }
        if (text.contains("nausea") || text.contains("feel sick") || text.contains("ji ghabrana")) {
            if (isNegated(text, "nausea")) {
                state.addFact("nausea", ClinicalFact.userReported("nausea", "absent", turn));
            } else {
                state.getSymptoms().put("nausea", ClinicalFact.userReported("nausea", "present", turn));
                state.addFact("nausea", ClinicalFact.userReported("nausea", "present", turn));
            }
        }
        if (text.contains("joint") || text.contains("knee") || text.contains("shoulder") || text.contains("arthritis") || text.contains("jodo me dard")) {
            state.getSymptoms().put("joint_pain", ClinicalFact.userReported("joint_pain", "present", turn));
            state.addFact("joint_pain", ClinicalFact.userReported("joint_pain", "present", turn));
        }
        if (text.contains("dizziness") || text.contains("vertigo") || text.contains("lightheaded") || text.contains("chakkar")) {
            state.getSymptoms().put("dizziness", ClinicalFact.userReported("dizziness", "present", turn));
            state.addFact("dizziness", ClinicalFact.userReported("dizziness", "present", turn));
        }
        if (text.contains("cold") || text.contains("runny nose") || text.contains("congestion") || text.contains("sinus") || text.contains("sneez")) {
            state.getSymptoms().put("cold_symptoms", ClinicalFact.userReported("cold_symptoms", "present", turn));
            state.addFact("cold_symptoms", ClinicalFact.userReported("cold_symptoms", "present", turn));
        }
        if (text.contains("ear") && (text.contains("pain") || text.contains("ache") || text.contains("block") || text.contains("discharge"))) {
            state.getSymptoms().put("ear_pain", ClinicalFact.userReported("ear_pain", "present", turn));
            state.addFact("ear_pain", ClinicalFact.userReported("ear_pain", "present", turn));
        }
        if (text.contains("chest") && (text.contains("pain") || text.contains("tight") || text.contains("pressure") || text.contains("heaviness"))) {
            state.getSymptoms().put("chest_symptoms", ClinicalFact.userReported("chest_symptoms", "present", turn));
            state.addFact("chest_symptoms", ClinicalFact.userReported("chest_symptoms", "present", turn));
        }
        if (text.contains("urin") || text.contains("urnie") || text.contains("urien") || text.contains("dysuria") || text.contains("peeing") || text.contains("pee") || text.contains("bladder") || text.contains("micturition") || text.contains("uti")) {
            state.getSymptoms().put("dysuria", ClinicalFact.userReported("dysuria", "present", turn));
            state.addFact("dysuria", ClinicalFact.userReported("dysuria", "present", turn));
        }
        if (text.contains("eye") || text.contains("blur") || text.contains("vision")) {
            state.getSymptoms().put("eye_symptoms", ClinicalFact.userReported("eye_symptoms", "present", turn));
            state.addFact("eye_symptoms", ClinicalFact.userReported("eye_symptoms", "present", turn));
        }
        if (text.contains("back") && text.contains("pain")) {
            state.getSymptoms().put("back_pain", ClinicalFact.userReported("back_pain", "present", turn));
            state.addFact("back_pain", ClinicalFact.userReported("back_pain", "present", turn));
        }
        if (text.contains("vomit")) {
            if (isNegated(text, "vomit") || text.contains("no vomit") || text.contains("not vomit") || text.contains("without vomit")) {
                state.addFact("vomiting", ClinicalFact.userReported("vomiting", "absent", turn));
            } else {
                state.getSymptoms().put("vomiting", ClinicalFact.userReported("vomiting", "present", turn));
                state.addFact("vomiting", ClinicalFact.userReported("vomiting", "present", turn));
            }
        }

        // Trauma, Wounds, Cuts & Bleeding
        if (text.contains("cut") || text.contains("wound") || text.contains("lacerat") || text.contains("puncture") || text.contains("slash") || text.contains("kat gaya") || (text.contains("bleed") && !text.contains("nosebleed"))) {
            if (!isNegated(text, "cut") && !isNegated(text, "wound") && !isNegated(text, "bleed")) {
                state.getSymptoms().put("laceration_wound", ClinicalFact.userReported("laceration_wound", "present", turn));
                state.addFact("laceration_wound", ClinicalFact.userReported("laceration_wound", "present", turn));
            }
        }

        // Thermal Burns & Scalds
        if (text.contains("scald") || text.contains("jal gaya") || ((text.contains("burn") || text.contains("blister")) && !text.contains("urin") && !text.contains("pee") && !text.contains("dysuria") && !text.contains("heartburn"))) {
            if (!isNegated(text, "burn") && !isNegated(text, "scald")) {
                state.getSymptoms().put("burn_injury", ClinicalFact.userReported("burn_injury", "present", turn));
                state.addFact("burn_injury", ClinicalFact.userReported("burn_injury", "present", turn));
            }
        }

        // Sprains & Strains
        if (text.contains("sprain") || text.contains("twist") || text.contains("twisted") || text.contains("moch") || text.contains("rolled")) {
            if (!isNegated(text, "sprain")) {
                state.getSymptoms().put("sprain_strain", ClinicalFact.userReported("sprain_strain", "present", turn));
                state.addFact("sprain_strain", ClinicalFact.userReported("sprain_strain", "present", turn));
            }
        }

        // Dental & Odontalgia
        if (text.contains("tooth") || text.contains("teeth") || text.contains("toothache") || text.contains("dant") || (text.contains("gum") && text.contains("bleed"))) {
            if (!isNegated(text, "tooth") && !isNegated(text, "toothache")) {
                state.getSymptoms().put("dental_pain", ClinicalFact.userReported("dental_pain", "present", turn));
                state.addFact("dental_pain", ClinicalFact.userReported("dental_pain", "present", turn));
            }
        }

        // 5. Medications mentioned
        Matcher medMatcher = MEDICATIONS.matcher(text);
        while (medMatcher.find()) {
            String med = medMatcher.group(1).trim();
            if (!state.getMedications().contains(med)) {
                state.getMedications().add(med);
            }
            state.addFact("medication_" + med, ClinicalFact.userReported("medication", med, turn));
        }
    }
}

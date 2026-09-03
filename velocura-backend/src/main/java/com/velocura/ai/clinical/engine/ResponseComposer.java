package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.ChatResponse;
import com.velocura.dto.TriageResponse;
import com.velocura.dto.DifferentialDiagnosis;
import com.velocura.dto.HomeCareRemedy;
import com.velocura.dto.OtcMedication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ResponseComposer: Assembles concise, empathetic clinical responses,
 * attaches contextual quick-replies, and ensures 100% backward-compatibility with existing DTOs.
 */
@Component
public class ResponseComposer {

    public ChatResponse composeEmergency(
            SafetyScreeningResult emergencyResult,
            ClinicalConversationState state) {

        ChatResponse response = new ChatResponse();
        response.setEmergency(true);
        response.setIntent("SYMPTOM_TRIAGE");
        response.setRiskLevel("CRITICAL");
        response.setNextAction(NextAction.ESCALATE.name());
        response.setPhase(ClinicalPhase.ESCALATION.name());

        String message = emergencyResult.getEmergencyReason() + "\n\n" + emergencyResult.getEmergencyAdvice();
        response.setClinicalMessage(message);
        response.setQuickReplies(List.of("Called Emergency Services", "Need Immediate Telehealth", "Someone is with me"));

        if (state != null && state.getPatientContext() != null) {
            response.setPatientRelationship(state.getPatientContext().getRelationship());
        }

        // Populate backward-compatible TriageResponse
        TriageResponse triage = TriageResponse.builder()
                .doctorMessage(message)
                .riskLevel("CRITICAL")
                .requiresImmediateTelehealth(true)
                .specialistDepartment("Emergency Medicine / Cardiology")
                .differentialDiagnoses(List.of(
                        new DifferentialDiagnosis("BA41", "Acute Coronary Syndrome / Acute Ischemic Event", "HIGH", "Immediate emergency evaluation required")
                ))
                .homeCareRemedies(List.of(
                        new HomeCareRemedy("Rest in comfortable seated position", "Minimizes physiological exertion")
                ))
                .suggestedOtc(new ArrayList<>()) // Strictly empty for critical emergencies
                .redFlags(emergencyResult.getRedFlags())
                .followUpAdvice("Call local emergency services immediately")
                .build();

        response.setTriage(triage);
        return response;
    }

    public ChatResponse composeStandard(
            String validatedMessage,
            ClinicalConversationState state,
            NextBestQuestionEngine.QuestionDecision questionDecision,
            String rawInput) {

        ChatResponse response = new ChatResponse();
        ClinicalIntent intent = state.getIntent();

        response.setEmergency(false);
        response.setRiskLevel(state.getCurrentRiskLevel().name());
        response.setNextAction(questionDecision.getNextAction().name());
        response.setPhase(state.getCurrentPhase().name());
        response.setQuickReplies(questionDecision.getQuickReplies());
        response.setClinicalMessage(validatedMessage);

        if (state.getPatientContext() != null) {
            response.setPatientRelationship(state.getPatientContext().getRelationship());
        }

        // Map to backward-compatible intents
        if (intent == ClinicalIntent.GENERAL_CONVERSATION) {
            response.setIntent("CASUAL");
            response.setCasualReply(validatedMessage);
        } else if (intent == ClinicalIntent.EDUCATIONAL || intent == ClinicalIntent.TEST_INTERPRETATION
                || intent == ClinicalIntent.MEDICATION_INFORMATION || intent == ClinicalIntent.SELF_CARE) {
            response.setIntent("MEDICAL_QA");
            response.setMedicalQaReply(validatedMessage);
        } else {
            // SYMPTOM_ASSESSMENT, CLARIFICATION, MEDICATION_SAFETY, FOLLOW_UP
            response.setIntent("SYMPTOM_TRIAGE");

            // Build structured TriageResponse for compatibility
            TriageResponse triage = buildStructuredTriage(validatedMessage, state, rawInput);
            response.setTriage(triage);
        }

        return response;
    }

    private TriageResponse buildStructuredTriage(String message, ClinicalConversationState state, String rawInput) {
        StringBuilder symptomContext = new StringBuilder();
        if (rawInput != null) symptomContext.append(rawInput.toLowerCase()).append(" ");
        if (message != null) symptomContext.append(message.toLowerCase()).append(" ");
        if (state != null && state.getSymptoms() != null) {
            for (String s : state.getSymptoms().keySet()) {
                symptomContext.append(s.toLowerCase()).append(" ");
            }
        }
        String lower = symptomContext.toString();

        String dept = "General Medicine";
        String risk = "LOW";
        List<DifferentialDiagnosis> diffs = new ArrayList<>();
        List<HomeCareRemedy> home = new ArrayList<>();
        List<OtcMedication> otc = new ArrayList<>();
        List<String> redFlags = new ArrayList<>();

        boolean hasCutWord = Pattern.compile("(?i)\\b(cut|cuts|cutting|wound|wounds|lacerat|laceration|kat\\s*gaya|laceration_wound)\\b").matcher(lower).find();
        if (hasCutWord) {
            dept = "Emergency Medicine / Surgery";
            risk = "MEDIUM";
            diffs.add(new DifferentialDiagnosis("NE81.0", "Acute Laceration / Open Wound", "HIGH", "Cutaneous laceration with dermal disruption"));
            home.add(new HomeCareRemedy("Wash wound under clean tap water for 3-5 minutes and apply firm direct pressure with clean gauze", "Flushes bacterial debris and arrests bleeding"));
            otc.add(new OtcMedication("Bacitracin / Neosporin Topical Ointment", "Antimicrobial barrier protection for minor wounds", "Apply thin film to clean wound 1-2 times daily and cover with sterile dressing", "Deep puncture wounds or animal bites"));
            redFlags.add("Continuous bleeding not stopping after 10 minutes of direct pressure");
            redFlags.add("Loss of sensation, numbness, or inability to move joint/finger");
            redFlags.add("Wound caused by rusty metal (tetanus booster required if > 5-10 yrs)");
        } else if (lower.contains("burn_injury") || lower.contains("scald") || lower.contains("jal gaya") || (lower.contains("burn") && !lower.contains("urin") && !lower.contains("pee") && !lower.contains("dysuria") && !lower.contains("heartburn"))) {
            dept = "Emergency Medicine / Dermatology";
            risk = "MEDIUM";
            diffs.add(new DifferentialDiagnosis("ND90.0", "Acute Thermal Burn / Scald", "HIGH", "Thermal dermal injury requiring immediate barrier cooling"));
            home.add(new HomeCareRemedy("Cool burn under cool running tap water for 15-20 minutes; do NOT apply ice, butter, or pop blisters", "Arrests thermal progression in tissue"));
            otc.add(new OtcMedication("Silver Sulfadiazine 1% Cream / Pure Aloe Vera Gel", "Soothing antimicrobial barrier for superficial burns", "Apply thin layer to cooled clean burn 1-2 times daily", "Sulfa allergy; avoid near eyes"));
            redFlags.add("Burn larger than palm size or involving face, hands, feet, or moving joints");
            redFlags.add("Third-degree burn with white, charred, or numb skin");
        } else if (lower.contains("sprain") || lower.contains("twist") || lower.contains("moch") || lower.contains("sprain_strain")) {
            dept = "Orthopedics";
            risk = "MILD";
            diffs.add(new DifferentialDiagnosis("FB50.0", "Acute Sprain / Joint Strain", "HIGH", "Traumatic ligamentous stretching or strain"));
            home.add(new HomeCareRemedy("Follow R.I.C.E. protocol: Rest, Ice 15m every 2-3h, Compression bandage, Elevate above heart", "Minimizes swelling and mechanical strain"));
            otc.add(new OtcMedication("Topical Diclofenac Gel 1.16%", "Non-steroidal anti-inflammatory pain relief", "Gently massage 2-4g onto affected joint 3 to 4 times daily", "Broken or abraded skin"));
            redFlags.add("Inability to bear any weight and take 4 steps immediately after injury");
            redFlags.add("Visible bone deformity, angulation, or open joint skin");
        } else if (lower.contains("tooth") || lower.contains("teeth") || lower.contains("dental") || lower.contains("dant")) {
            dept = "Dentistry";
            risk = "MILD";
            diffs.add(new DifferentialDiagnosis("DA00.0", "Acute Odontalgia / Dental Pulpitis", "HIGH", "Pulpal or periodontal inflammation"));
            home.add(new HomeCareRemedy("Rinse mouth gently with warm salt water (1/2 tsp salt) every 3 hours; do NOT place aspirin directly against gum", "Reduces bacterial plaque and osmotic pressure"));
            otc.add(new OtcMedication("Ibuprofen 400mg + Paracetamol 500mg", "Oral analgesic synergy for acute dental inflammation", "1 dose every 6-8 hours with food as needed", "Active stomach ulcer"));
            redFlags.add("Facial or jaw swelling spreading towards neck or eye");
            redFlags.add("Difficulty opening mouth wider than two fingers (trismus) or difficulty swallowing");
        } else if (lower.contains("urin") || lower.contains("burning urination") || lower.contains("dysuria")) {
            dept = "Urology";
            risk = "MILD";
            diffs.add(new DifferentialDiagnosis("GC08", "Cystitis", "HIGH", "Dysuria and pelvic discomfort"));
            home.add(new HomeCareRemedy("Hydrate with 3-4L water daily", "Flushes urinary pathogens"));
            otc.add(new OtcMedication("Potassium Citrate Liquid", "Urine alkalizer for burning micturition", "15ml diluted in full glass of water 3x daily", "Renal insufficiency"));
            redFlags.add("High fever with flank pain");
            redFlags.add("Visible blood in urine");
        } else if (lower.contains("eye") || lower.contains("blur") || lower.contains("vision") || lower.contains("ocular")) {
            dept = "Ophthalmology";
            risk = "MILD";
            diffs.add(new DifferentialDiagnosis("9A60.0", "Allergic Conjunctivitis / Eye Strain", "HIGH", "Ocular irritation and visual strain"));
            home.add(new HomeCareRemedy("Cold sterile eye compress and 20-20-20 screen rest rule", "Relieves ocular fatigue and vascular congestion"));
            otc.add(new OtcMedication("Carboxymethylcellulose 0.5% Eye Drops", "Preservative-free tear lubricant", "1-2 drops 3-4 times daily", "Do not touch dropper tip to eye"));
            redFlags.add("Sudden loss of visual acuity or severe ocular pain");
        } else if (lower.contains("back") || lower.contains("spine")) {
            dept = "Orthopedics";
            risk = "MEDIUM";
            diffs.add(new DifferentialDiagnosis("FB84.1", "Lumbar Disc Disorder", "HIGH", "Low back pain with sitting discomfort"));
            home.add(new HomeCareRemedy("R.I.C.E. protocol and lumbar support", "Reduces mechanical disc strain"));
            otc.add(new OtcMedication("Ibuprofen 400mg with food", "Oral NSAID anti-inflammatory pain relief", "1 tablet every 8 hours with meals", "Peptic ulcer disease"));
            redFlags.add("Progressive leg numbness or foot drop");
        } else if (lower.contains("headache") || lower.contains("head pain") || lower.contains("migraine") || lower.contains("sar dard")) {
            dept = "Neurology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("8A80", "Primary Headache Disorder / Migraine", "HIGH", "Cephalea presenting as throbbing or tension pressure"));
            home.add(new HomeCareRemedy("Rest in a dark quiet room and hydrate", "Reduces neurovascular stimulation"));
            otc.add(new OtcMedication("Paracetamol 500mg or Ibuprofen 400mg", "Analgesic for acute headache relief", "1 tablet with water as needed (max 3x daily)", "Active stomach ulcer or liver impairment"));
            redFlags.add("Sudden thunderclap severity within seconds");
            redFlags.add("Stiff neck and fever");
        } else if (lower.contains("stomach") || lower.contains("abdom") || lower.contains("pet dard") || lower.contains("cramp")) {
            dept = "Gastroenterology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("DD90", "Acute Dyspepsia / Gastritis", "HIGH", "Visceral irritation and mucosal acidity"));
            home.add(new HomeCareRemedy("Bland diet (bananas, rice, toast) and warm water", "Soothes gastric mucosal lining"));
            otc.add(new OtcMedication("Antacid gel / Famotidine 20mg", "Reduces gastric acid hypersecretion", "10ml or 1 tablet 30 minutes before meals", "Severe kidney disease"));
            redFlags.add("Severe persistent vomiting or inability to keep fluids");
            redFlags.add("Black tarry stools");
        } else if (lower.contains("throat") || lower.contains("gala") || lower.contains("pharyng")) {
            dept = "ENT / Otolaryngology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("CA02", "Acute Pharyngitis", "HIGH", "Pharyngeal mucosal erythema and throat discomfort"));
            home.add(new HomeCareRemedy("Warm saline gargles 3 times daily", "Reduces local edema and clears bacterial debris"));
            otc.add(new OtcMedication("Antiseptic Lozenges (Amylmetacresol)", "Provides local anesthetic and soothing relief", "Dissolve 1 lozenge slowly in mouth every 2-3 hours", "Do not chew or swallow whole"));
            redFlags.add("Difficulty swallowing liquids or breathing");
        } else if (lower.contains("rash") || lower.contains("hives") || lower.contains("itch") || lower.contains("khujli")) {
            dept = "Dermatology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("EA80", "Allergic Contact Dermatitis / Urticaria", "HIGH", "Erythematous cutaneous reaction with pruritus"));
            home.add(new HomeCareRemedy("Cool compress and mild fragrance-free moisturizer", "Calms epidermal barrier irritation"));
            otc.add(new OtcMedication("Cetirizine 10mg / Calamine Lotion", "Second-generation antihistamine to relieve itching", "1 tablet once daily at bedtime", "Avoid alcohol while taking"));
            redFlags.add("Swelling of lips, tongue, or airway");
            redFlags.add("Blistering or skin sloughing");
        } else if (lower.contains("diarrhea") || lower.contains("loose motion") || lower.contains("dast")) {
            dept = "Gastroenterology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("1A00", "Acute Infectious Gastroenteritis", "HIGH", "Watery bowel motions with electrolyte loss"));
            home.add(new HomeCareRemedy("Oral Rehydration Solution (ORS) after each loose motion", "Prevents hypovolemia and restores electrolytes"));
            otc.add(new OtcMedication("WHO-formula ORS Packets", "Balanced glucose-electrolyte fluid replacement", "1 packet dissolved in 1 liter clean water, sip frequently", "None for ORS"));
            redFlags.add("Severe dehydration or dry tongue with sunken eyes");
            redFlags.add("Blood or mucus in stool");
        } else if (lower.contains("joint") || lower.contains("knee") || lower.contains("arthrit")) {
            dept = "Orthopedics / Rheumatology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("FA00", "Acute Arthralgia / Joint Strain", "HIGH", "Articular inflammation and localized mechanical pain"));
            home.add(new HomeCareRemedy("R.I.C.E protocol (Rest, Ice for 15 minutes, Elevation)", "Reduces acute intra-articular inflammation"));
            otc.add(new OtcMedication("Topical Diclofenac Gel", "Local NSAID anti-inflammatory without systemic gut upset", "Apply thin layer to affected joint 3 times daily", "Broken skin"));
            redFlags.add("Hot, intensely red, single swollen joint with fever");
        } else if (lower.contains("cough") || lower.contains("phlegm") || lower.contains("mucus")) {
            dept = "Pulmonology";
            risk = "LOW";
            diffs.add(new DifferentialDiagnosis("CA20", "Acute Bronchitis", "HIGH", "Persistent cough with airway hyperreactivity"));
            home.add(new HomeCareRemedy("Steam inhalation and warm saline gargling", "Relieves pharyngeal irritation"));
            if (lower.contains("productive") || lower.contains("phlegm") || lower.contains("mucus") || lower.contains("green") || lower.contains("yellow")) {
                otc.add(new OtcMedication("Guaifenesin 100mg / Ambroxol Syrup", "Expectorant & mucolytic agent (thins bronchial phlegm)", "10ml every 6-8 hours with full glass of water", "Do NOT use cough suppressants for productive cough"));
            } else {
                otc.add(new OtcMedication("Dextromethorphan HBr Syrup", "Cough suppressant for dry irritant cough", "10ml every 6-8 hours as needed", "Do not exceed recommended dose"));
            }
            redFlags.add("Blood in sputum");
        } else if (lower.contains("chest") && (lower.contains("pain") || lower.contains("pressure") || lower.contains("arm"))) {
            dept = "Emergency Medicine / Cardiology";
            risk = "CRITICAL";
            diffs.add(new DifferentialDiagnosis("BA41", "Acute Myocardial Infarction", "HIGH", "Crushing chest pressure with arm radiation"));
            home.add(new HomeCareRemedy("Rest in seated position", "Minimizes cardiac oxygen demand"));
            redFlags.add("Radiating jaw/arm pain");
        } else {
            diffs.add(new DifferentialDiagnosis("CA45", "Acute Viral Syndrome", "HIGH", "Consistent with acute viral symptoms"));
            home.add(new HomeCareRemedy("Adequate oral hydration and rest", "Supports immune clearance"));
            otc.add(new OtcMedication("Paracetamol 500mg", "Antipyretic and mild analgesic", "1 tablet as needed for body ache (max 3g/day)", "Hepatic impairment"));
            redFlags.add("High persistent fever above 103F");
        }

        return TriageResponse.builder()
                .doctorMessage(message)
                .riskLevel(risk)
                .requiresImmediateTelehealth("CRITICAL".equalsIgnoreCase(risk))
                .specialistDepartment(dept)
                .differentialDiagnoses(diffs)
                .homeCareRemedies(home)
                .suggestedOtc("CRITICAL".equalsIgnoreCase(risk) ? new ArrayList<>() : otc)
                .redFlags(redFlags)
                .followUpAdvice("Monitor over next 24-48 hours. Consult a specialist if symptoms persist.")
                .build();
    }
}

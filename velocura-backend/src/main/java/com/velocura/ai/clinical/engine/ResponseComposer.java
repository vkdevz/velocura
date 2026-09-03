package com.velocura.ai.clinical.engine;

import com.velocura.ai.clinical.safety.SafetyScreeningResult;
import com.velocura.ai.clinical.state.*;
import com.velocura.dto.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        } else if (intent == ClinicalIntent.EDUCATIONAL || intent == ClinicalIntent.TEST_INTERPRETATION || intent == ClinicalIntent.MEDICATION_INFORMATION) {
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
        String lower = rawInput != null ? rawInput.toLowerCase() : "";
        String dept = "General Medicine";
        String risk = "LOW";
        List<DifferentialDiagnosis> diffs = new ArrayList<>();
        List<HomeCareRemedy> home = new ArrayList<>();
        List<OtcMedication> otc = new ArrayList<>();
        List<String> redFlags = new ArrayList<>();

        if (lower.contains("urin") || lower.contains("burning urination") || lower.contains("dysuria")) {
            dept = "Urology";
            risk = "MILD";
            diffs.add(new DifferentialDiagnosis("GC08", "Cystitis", "HIGH", "Dysuria and pelvic discomfort"));
            home.add(new HomeCareRemedy("Hydrate with 3-4L water daily", "Flushes urinary pathogens"));
            otc.add(new OtcMedication("Potassium Citrate Liquid", "Urine alkalizer for burning micturition", "15ml diluted in full glass of water 3x daily", "Renal insufficiency"));
            redFlags.add("High fever with flank pain");
            redFlags.add("Visible blood in urine");
        } else if (lower.contains("eye") && (lower.contains("red") || lower.contains("itch") || lower.contains("watery"))) {
            dept = "Ophthalmology";
            risk = "MILD";
            diffs.add(new DifferentialDiagnosis("9A60.0", "Allergic Conjunctivitis", "HIGH", "Ocular erythema and pruritus"));
            home.add(new HomeCareRemedy("Cold sterile eye compress", "Reduces ocular itching and edema"));
            otc.add(new OtcMedication("Carboxymethylcellulose 0.5% Eye Drops", "Preservative-free tear lubricant", "1-2 drops 3-4 times daily", "Do not touch dropper tip to eye"));
            redFlags.add("Decreased visual acuity");
        } else if (lower.contains("back") || lower.contains("spine")) {
            dept = "Orthopedics";
            risk = "MEDIUM";
            diffs.add(new DifferentialDiagnosis("FB84.1", "Lumbar Disc Disorder", "HIGH", "Low back pain with sitting discomfort"));
            home.add(new HomeCareRemedy("R.I.C.E. protocol and lumbar support", "Reduces mechanical disc strain"));
            otc.add(new OtcMedication("Ibuprofen 400mg with food", "Oral NSAID anti-inflammatory pain relief", "1 tablet every 8 hours with meals", "Peptic ulcer disease"));
            redFlags.add("Progressive leg numbness or foot drop");
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

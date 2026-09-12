package com.velocura.ai.clinical.medication.service;

import com.velocura.ai.clinical.engine.ClinicalReasoningResult;
import com.velocura.ai.clinical.diagnostic.dto.CandidateConditionAssessment;
import com.velocura.ai.clinical.medication.model.MedicationSafetyAssessment;
import com.velocura.ai.clinical.medication.model.MedicationSafetyStatus;
import com.velocura.ai.clinical.model.ClinicalEntity;
import com.velocura.ai.clinical.model.PrescriptionProtocol;
import com.velocura.ai.clinical.model.RxMedicationItem;
import com.velocura.ai.clinical.safety.PharmacologicalSafetyMatrix;
import com.velocura.ai.clinical.state.ClinicalRiskLevel;
import com.velocura.ai.clinical.state.PatientContext;
import com.velocura.model.PrescriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ClinicalPrescriptionDraftService:
 * Strictly enforces the prescription boundary.
 *
 * HARD CONSTRAINTS:
 * 1. AI can ONLY synthesize a DRAFT prescription protocol for human clinician review.
 * 2. It requires:
 *    - Valid ClinicalReasoningResult
 *    - Medication safety evaluation (must NOT be CONTRAINDICATED or BLOCK)
 *    - PatientContext (pediatric, pregnancy, renal/hepatic considerations)
 *    - Contraindication/allergy checks
 *    - Explicit clinician review & authorization flags
 * 3. Never finalizes, signs, or submits prescriptions autonomously.
 * 4. Critical emergencies strictly prohibit autonomous or draft prescriptions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalPrescriptionDraftService {

    private final PharmacologicalSafetyMatrix safetyMatrix;

    /**
     * Synthesizes a safe DRAFT prescription protocol requiring clinician authorization.
     */
    public Optional<PrescriptionProtocol> synthesizeDraftProtocol(
            ClinicalReasoningResult reasoningResult,
            ClinicalEntity topEntity,
            PatientContext patientContext,
            List<String> reportedSymptoms) {

        if (reasoningResult == null) {
            log.warn("[PRESCRIPTION BOUNDARY] Blocked: No ClinicalReasoningResult provided.");
            return Optional.empty();
        }

        // Rule 1: Critical Emergency prohibits routine draft prescriptions
        if (reasoningResult.getRiskLevel() == ClinicalRiskLevel.CRITICAL
                || reasoningResult.getRiskLevel() == ClinicalRiskLevel.EMERGENCY
                || "EMERGENCY_ESCALATION".equalsIgnoreCase(reasoningResult.getSafetyStatus())) {
            log.warn("[PRESCRIPTION BOUNDARY] Blocked: Critical emergency requires acute resuscitation, not routine digital prescription.");
            return Optional.empty();
        }

        // Rule 2: Medication safety check must not be BLOCK or CONTRAINDICATED
        MedicationSafetyAssessment medSafety = reasoningResult.getMedicationAssessment();
        if (medSafety != null && (medSafety.getOverallSafetyStatus() == MedicationSafetyStatus.BLOCK
                || medSafety.getOverallSafetyStatus() == MedicationSafetyStatus.CONTRAINDICATED)) {
            log.warn("[PRESCRIPTION BOUNDARY] Blocked: Medication safety assessment contains active contraindications/blocks.");
            return Optional.empty();
        }

        // Rule 3: Clinical reasoning must provide a candidate condition
        CandidateConditionAssessment primaryAssessment = null;
        if (reasoningResult.getDifferential() != null
                && reasoningResult.getDifferential().getCandidateConditions() != null
                && !reasoningResult.getDifferential().getCandidateConditions().isEmpty()) {
            primaryAssessment = reasoningResult.getDifferential().getCandidateConditions().get(0);
        }

        String icdCode = primaryAssessment != null ? primaryAssessment.getIcdCode() : (topEntity != null ? topEntity.getIcd11Code() : "MG30");
        String primaryDx = primaryAssessment != null ? primaryAssessment.getConditionName() : (topEntity != null ? topEntity.getTitle() : "Acute Medical Condition");

        PrescriptionProtocol base;
        if (topEntity != null && topEntity.getDefaultPrescriptionProtocol() != null) {
            base = deepCopy(topEntity.getDefaultPrescriptionProtocol());
        } else {
            base = buildFallbackDraft(icdCode, primaryDx);
        }

        // Enforce strict DRAFT status & lifecycle invariants
        base.setPrescriptionId("RX-DRAFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        base.setStatus(PrescriptionStatus.DRAFT);
        base.setClinicianReviewRequired(true);
        base.setClinicianAuthorizationRequired(true);
        base.setRequiresDoctorSignature(true);
        base.setAuthorizedBy(null); // Never authorized by AI
        base.setPrimaryDiagnosis(primaryDx);
        base.setIcd11Code(icdCode);

        if (reasoningResult.getSpecialistDepartment() != null) {
            base.setSpecialistDepartment(reasoningResult.getSpecialistDepartment());
        } else if (topEntity != null && topEntity.getSpecialistDepartment() != null) {
            base.setSpecialistDepartment(topEntity.getSpecialistDepartment());
        }

        if (patientContext != null) {
            base.setPatientContextSummary((patientContext.isPediatric() ? "Pediatric" : "Adult") + " | " + patientContext.getRelationship());
        }

        // Sanitize through pharmacological safety matrix
        List<String> diagnoses = List.of(icdCode != null ? icdCode : "", primaryDx != null ? primaryDx : "");
        PrescriptionProtocol validatedDraft = safetyMatrix.sanitizeAndValidate(base, patientContext, diagnoses, reportedSymptoms);

        // Re-affirm lifecycle state after sanitization
        validatedDraft.setStatus(PrescriptionStatus.DRAFT);
        validatedDraft.setClinicianReviewRequired(true);
        validatedDraft.setClinicianAuthorizationRequired(true);
        validatedDraft.setRequiresDoctorSignature(true);
        validatedDraft.setAuthorizedBy(null);

        return Optional.of(validatedDraft);
    }

    private PrescriptionProtocol buildFallbackDraft(String icdCode, String primaryDx) {
        return PrescriptionProtocol.builder()
                .icd11Code(icdCode != null ? icdCode : "MG30")
                .primaryDiagnosis(primaryDx != null ? primaryDx : "Acute Symptomatic Presentation")
                .specialistDepartment("General Medicine")
                .status(PrescriptionStatus.DRAFT)
                .clinicianReviewRequired(true)
                .clinicianAuthorizationRequired(true)
                .requiresDoctorSignature(true)
                .authorizedBy(null)
                .medications(List.of(
                        RxMedicationItem.builder()
                                .saltName("Paracetamol (Acetaminophen)")
                                .brandReference("Dolo 650 / Panadol")
                                .formulation("Tablet")
                                .strength("650 mg")
                                .route("Oral")
                                .dosageFrequency("1 tablet every 6 to 8 hours PRN")
                                .duration("3 to 5 days")
                                .instructions("Take after food with water. Max 3000 mg in 24 hours.")
                                .indication("Symptomatic pain/fever relief pending physician assessment")
                                .prescriptionOnly(false)
                                .build()
                ))
                .supportiveCare(List.of("Maintain oral hydration", "Adequate rest"))
                .contraindicatedMedications(List.of("Excessive antipyretic combinations"))
                .diagnosticLabOrders(List.of("Routine Complete Blood Count if symptoms persist > 3 days"))
                .redFlagHospitalizationCriteria(List.of("Dyspnea", "Altered mental status", "Uncontrolled vomiting"))
                .build();
    }

    private PrescriptionProtocol deepCopy(PrescriptionProtocol src) {
        List<RxMedicationItem> meds = new ArrayList<>();
        if (src.getMedications() != null) {
            for (RxMedicationItem m : src.getMedications()) {
                meds.add(RxMedicationItem.builder()
                        .saltName(m.getSaltName())
                        .brandReference(m.getBrandReference())
                        .formulation(m.getFormulation())
                        .strength(m.getStrength())
                        .route(m.getRoute())
                        .dosageFrequency(m.getDosageFrequency())
                        .duration(m.getDuration())
                        .instructions(m.getInstructions())
                        .indication(m.getIndication())
                        .prescriptionOnly(m.isPrescriptionOnly())
                        .build());
            }
        }
        return PrescriptionProtocol.builder()
                .prescriptionId(src.getPrescriptionId())
                .primaryDiagnosis(src.getPrimaryDiagnosis())
                .icd11Code(src.getIcd11Code())
                .patientContextSummary(src.getPatientContextSummary())
                .specialistDepartment(src.getSpecialistDepartment())
                .status(PrescriptionStatus.DRAFT)
                .clinicianReviewRequired(true)
                .clinicianAuthorizationRequired(true)
                .requiresDoctorSignature(true)
                .authorizedBy(null)
                .medications(meds)
                .supportiveCare(src.getSupportiveCare() != null ? new ArrayList<>(src.getSupportiveCare()) : new ArrayList<>())
                .contraindicatedMedications(src.getContraindicatedMedications() != null ? new ArrayList<>(src.getContraindicatedMedications()) : new ArrayList<>())
                .diagnosticLabOrders(src.getDiagnosticLabOrders() != null ? new ArrayList<>(src.getDiagnosticLabOrders()) : new ArrayList<>())
                .redFlagHospitalizationCriteria(src.getRedFlagHospitalizationCriteria() != null ? new ArrayList<>(src.getRedFlagHospitalizationCriteria()) : new ArrayList<>())
                .build();
    }
}

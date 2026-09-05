package com.velocura.service.clinical;

import com.velocura.model.ClinicalValidationRecord;
import com.velocura.repository.ClinicalValidationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ClinicalValidationService:
 * Powers the proprietary Closed-Loop Physician Validation Flywheel.
 * Captures physician agreement/disagreement ground truth on AI triage diagnoses,
 * computing accuracy concordance rates and clinical audit benchmarks.
 */
@Service
public class ClinicalValidationService {

    private final ClinicalValidationRepository validationRepository;

    public ClinicalValidationService(ClinicalValidationRepository validationRepository) {
        this.validationRepository = validationRepository;
    }

    public static class ValidationSubmissionRequest {
        private String sessionId;
        private Long appointmentId;
        private Long doctorUserId;
        private String doctorName;
        private String aiPrimaryDiagnosis;
        private String aiIcdCode;
        private String aiConfidence;
        private String agreementStatus; // AGREE | PARTIALLY_AGREE | DISAGREE
        private String physicianConfirmedDiagnosis;
        private String physicianConfirmedIcd11;
        private String discrepancyReason;
        private String clinicalNotes;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Long getAppointmentId() { return appointmentId; }
        public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

        public Long getDoctorUserId() { return doctorUserId; }
        public void setDoctorUserId(Long doctorUserId) { this.doctorUserId = doctorUserId; }

        public String getDoctorName() { return doctorName; }
        public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

        public String getAiPrimaryDiagnosis() { return aiPrimaryDiagnosis; }
        public void setAiPrimaryDiagnosis(String aiPrimaryDiagnosis) { this.aiPrimaryDiagnosis = aiPrimaryDiagnosis; }

        public String getAiIcdCode() { return aiIcdCode; }
        public void setAiIcdCode(String aiIcdCode) { this.aiIcdCode = aiIcdCode; }

        public String getAiConfidence() { return aiConfidence; }
        public void setAiConfidence(String aiConfidence) { this.aiConfidence = aiConfidence; }

        public String getAgreementStatus() { return agreementStatus; }
        public void setAgreementStatus(String agreementStatus) { this.agreementStatus = agreementStatus; }

        public String getPhysicianConfirmedDiagnosis() { return physicianConfirmedDiagnosis; }
        public void setPhysicianConfirmedDiagnosis(String physicianConfirmedDiagnosis) { this.physicianConfirmedDiagnosis = physicianConfirmedDiagnosis; }

        public String getPhysicianConfirmedIcd11() { return physicianConfirmedIcd11; }
        public void setPhysicianConfirmedIcd11(String physicianConfirmedIcd11) { this.physicianConfirmedIcd11 = physicianConfirmedIcd11; }

        public String getDiscrepancyReason() { return discrepancyReason; }
        public void setDiscrepancyReason(String discrepancyReason) { this.discrepancyReason = discrepancyReason; }

        public String getClinicalNotes() { return clinicalNotes; }
        public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }
    }

    @Transactional
    public ClinicalValidationRecord recordValidation(ValidationSubmissionRequest req) {
        String agreement = (req.getAgreementStatus() != null ? req.getAgreementStatus().toUpperCase() : "AGREE");
        if (!agreement.equals("AGREE") && !agreement.equals("PARTIALLY_AGREE") && !agreement.equals("DISAGREE")) {
            agreement = "AGREE";
        }

        ClinicalValidationRecord record = ClinicalValidationRecord.builder()
                .sessionId(req.getSessionId())
                .appointmentId(req.getAppointmentId())
                .doctorUserId(req.getDoctorUserId())
                .doctorName(req.getDoctorName() != null ? req.getDoctorName() : "Attending Physician")
                .aiPrimaryDiagnosis(req.getAiPrimaryDiagnosis())
                .aiIcdCode(req.getAiIcdCode())
                .aiConfidence(req.getAiConfidence())
                .agreementStatus(agreement)
                .physicianConfirmedDiagnosis(req.getPhysicianConfirmedDiagnosis())
                .physicianConfirmedIcd11(req.getPhysicianConfirmedIcd11())
                .discrepancyReason(req.getDiscrepancyReason())
                .clinicalNotes(req.getClinicalNotes())
                .createdAt(LocalDateTime.now())
                .build();

        return validationRepository.save(record);
    }

    public Map<String, Object> calculateConcordanceMetrics() {
        List<ClinicalValidationRecord> all = validationRepository.findAll();
        long total = all.size();
        if (total == 0) {
            return Map.of(
                    "totalAuditedCases", 0,
                    "concordanceRatePercentage", 100.0,
                    "agreeCount", 0,
                    "partiallyAgreeCount", 0,
                    "disagreeCount", 0,
                    "recentAudits", Collections.emptyList()
            );
        }

        long agree = all.stream().filter(r -> "AGREE".equalsIgnoreCase(r.getAgreementStatus())).count();
        long partial = all.stream().filter(r -> "PARTIALLY_AGREE".equalsIgnoreCase(r.getAgreementStatus())).count();
        long disagree = all.stream().filter(r -> "DISAGREE".equalsIgnoreCase(r.getAgreementStatus())).count();

        double concordanceRate = ((double)(agree + (partial * 0.5)) / total) * 100.0;
        concordanceRate = Math.round(concordanceRate * 10.0) / 10.0;

        List<Map<String, Object>> recent = all.stream()
                .sorted(Comparator.comparing(ClinicalValidationRecord::getCreatedAt).reversed())
                .limit(10)
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "doctorName", r.getDoctorName() != null ? r.getDoctorName() : "Doctor",
                        "aiDiagnosis", r.getAiPrimaryDiagnosis() != null ? r.getAiPrimaryDiagnosis() : "Diagnosis",
                        "agreement", r.getAgreementStatus(),
                        "confirmedDiagnosis", r.getPhysicianConfirmedDiagnosis() != null ? r.getPhysicianConfirmedDiagnosis() : "Same",
                        "date", r.getCreatedAt().toString()
                ))
                .toList();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalAuditedCases", total);
        metrics.put("concordanceRatePercentage", concordanceRate);
        metrics.put("agreeCount", agree);
        metrics.put("partiallyAgreeCount", partial);
        metrics.put("disagreeCount", disagree);
        metrics.put("recentAudits", recent);

        return metrics;
    }
}

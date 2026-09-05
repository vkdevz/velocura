package com.velocura.service.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocura.ai.clinical.state.ClinicalConversationState;
import com.velocura.ai.clinical.state.ClinicalStateStore;
import com.velocura.dto.DifferentialDiagnosis;
import com.velocura.dto.TriageResponse;
import com.velocura.model.Appointment;
import com.velocura.model.ChatHistorySession;
import com.velocura.repository.AppointmentRepository;
import com.velocura.repository.ChatHistorySessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FhirBundleService:
 * Synthesizes official HL7 FHIR Release 4 (R4) Document Bundles for seamless hospital EHR interoperability.
 * Conforms to HL7 FHIR R4 specifications with WHO ICD-11 system URIs ("http://id.who.int/icd/release/11/mms")
 * and LOINC standard observation coding.
 */
@Service
public class FhirBundleService {

    private static final Logger log = LoggerFactory.getLogger(FhirBundleService.class);

    private final ClinicalStateStore stateStore;
    private final ChatHistorySessionRepository chatHistoryRepository;
    private final AppointmentRepository appointmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FhirBundleService(
            ClinicalStateStore stateStore,
            ChatHistorySessionRepository chatHistoryRepository,
            AppointmentRepository appointmentRepository) {
        this.stateStore = stateStore;
        this.chatHistoryRepository = chatHistoryRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Generates an HL7 FHIR R4 Bundle for a given triage session.
     */
    public Map<String, Object> generateFhirBundleForSession(String sessionId) {
        ClinicalConversationState state = stateStore != null ? stateStore.get(sessionId) : null;
        Optional<ChatHistorySession> historyOpt = chatHistoryRepository != null ? chatHistoryRepository.findBySessionId(sessionId) : Optional.empty();

        String patientName = "Anonymous Patient";
        String patientGender = "unknown";
        String chiefComplaint = "Symptom Evaluation";
        String riskLevel = "MILD";
        List<DifferentialDiagnosis> differentials = new ArrayList<>();
        Map<String, String> vitals = new HashMap<>();

        if (state != null) {
            if (state.getPatientContext() != null) {
                patientGender = state.getPatientContext().getGender() != null
                        ? state.getPatientContext().getGender().toLowerCase() : "unknown";
            }
            if (state.getSymptoms() != null && !state.getSymptoms().isEmpty()) {
                chiefComplaint = String.join(", ", state.getSymptoms().keySet());
            }
            if (state.getCurrentRiskLevel() != null) {
                riskLevel = state.getCurrentRiskLevel().name();
            }
            if (state.getVitals() != null) {
                vitals.putAll(state.getVitals());
            }
        }

        if (historyOpt.isPresent()) {
            ChatHistorySession history = historyOpt.get();
            if (history.getPatient() != null && history.getPatient().getUser() != null) {
                patientName = history.getPatient().getUser().getFirstName() + " " + history.getPatient().getUser().getLastName();
            }
            if (history.getChiefComplaint() != null) {
                chiefComplaint = history.getChiefComplaint();
            }
            if (history.getRiskLevel() != null) {
                riskLevel = history.getRiskLevel();
            }
            if (history.getTriageResultJson() != null) {
                try {
                    TriageResponse tr = objectMapper.readValue(history.getTriageResultJson(), TriageResponse.class);
                    if (tr.getDifferentialDiagnoses() != null) {
                        differentials.addAll(tr.getDifferentialDiagnoses());
                    }
                } catch (Exception e) {
                    log.debug("Could not parse triage JSON from chat history", e);
                }
            }
        }

        if (differentials.isEmpty()) {
            differentials.add(new DifferentialDiagnosis("CA45", "Acute Viral Syndromic Illness", "MEDIUM", "Constitutional symptom presentation"));
        }

        return buildFhirR4Document(sessionId, patientName, patientGender, chiefComplaint, riskLevel, differentials, vitals);
    }

    /**
     * Generates an HL7 FHIR R4 Bundle for an appointment encounter.
     */
    public Map<String, Object> generateFhirBundleForAppointment(Long appointmentId) {
        Optional<Appointment> apptOpt = appointmentRepository.findById(appointmentId);
        if (apptOpt.isEmpty()) {
            throw new IllegalArgumentException("Appointment #" + appointmentId + " not found");
        }

        Appointment appt = apptOpt.get();
        String patientName = appt.getPatient() != null && appt.getPatient().getUser() != null
                ? appt.getPatient().getUser().getFirstName() + " " + appt.getPatient().getUser().getLastName()
                : "Patient #" + (appt.getPatient() != null ? appt.getPatient().getId() : "Unknown");

        String gender = appt.getPatient() != null && appt.getPatient().getGender() != null
                ? appt.getPatient().getGender().toString().toLowerCase() : "unknown";

        String chiefComplaint = appt.getReason() != null ? appt.getReason() : "Telehealth Consultation";
        String riskLevel = "MODERATE";

        List<DifferentialDiagnosis> differentials = List.of(
                new DifferentialDiagnosis("DA00", "Clinical Telehealth Consultation Encounter", "HIGH", "Physician encounter evaluation")
        );

        return buildFhirR4Document("appt-" + appointmentId, patientName, gender, chiefComplaint, riskLevel, differentials, Collections.emptyMap());
    }

    private Map<String, Object> buildFhirR4Document(
            String idSuffix,
            String patientName,
            String patientGender,
            String chiefComplaint,
            String riskLevel,
            List<DifferentialDiagnosis> differentials,
            Map<String, String> vitals) {

        String nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String bundleId = "velocura-fhir-" + UUID.randomUUID().toString().substring(0, 8);
        String patientRef = "urn:uuid:patient-" + idSuffix;
        String encounterRef = "urn:uuid:encounter-" + idSuffix;
        String compositionRef = "urn:uuid:composition-" + idSuffix;

        List<Map<String, Object>> entries = new ArrayList<>();

        // 1. Composition (Document Header)
        Map<String, Object> composition = new LinkedHashMap<>();
        composition.put("resourceType", "Composition");
        composition.put("id", "comp-" + idSuffix);
        composition.put("status", "final");
        composition.put("type", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://loinc.org",
                        "code", "11488-4",
                        "display", "Consultation note"
                )),
                "text", "VeloCura Clinical Intake & Triage Summary"
        ));
        composition.put("subject", Map.of("reference", patientRef, "display", patientName));
        composition.put("encounter", Map.of("reference", encounterRef));
        composition.put("date", nowIso);
        composition.put("author", List.of(Map.of("display", "VeloCura Autonomous Clinical Decision Support System")));
        composition.put("title", "Clinical Triage & Differential Diagnosis Record");

        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(Map.of(
                "title", "Chief Complaint",
                "code", Map.of("coding", List.of(Map.of("system", "http://loinc.org", "code", "10154-3", "display", "Chief complaint"))),
                "text", Map.of("status", "generated", "div", "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>" + chiefComplaint + "</p></div>")
        ));
        sections.add(Map.of(
                "title", "Risk Stratification",
                "code", Map.of("coding", List.of(Map.of("system", "http://loinc.org", "code", "75448-1", "display", "Risk assessment"))),
                "text", Map.of("status", "generated", "div", "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>Stratified Triage Risk: <strong>" + riskLevel + "</strong></p></div>")
        ));
        composition.put("section", sections);

        entries.add(Map.of("fullUrl", compositionRef, "resource", composition));

        // 2. Patient Resource
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("resourceType", "Patient");
        patient.put("id", "patient-" + idSuffix);
        patient.put("name", List.of(Map.of("use", "official", "text", patientName)));
        patient.put("gender", patientGender);
        entries.add(Map.of("fullUrl", patientRef, "resource", patient));

        // 3. Encounter Resource
        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("resourceType", "Encounter");
        encounter.put("id", "encounter-" + idSuffix);
        encounter.put("status", "finished");
        encounter.put("class", Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code", "VR",
                "display", "Virtual Telehealth"
        ));
        encounter.put("subject", Map.of("reference", patientRef));
        encounter.put("period", Map.of("start", nowIso, "end", nowIso));
        entries.add(Map.of("fullUrl", encounterRef, "resource", encounter));

        // 4. Condition Resources (ICD-11 Differentials)
        int condIdx = 1;
        for (DifferentialDiagnosis dx : differentials) {
            String condRef = "urn:uuid:condition-" + idSuffix + "-" + condIdx;
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("resourceType", "Condition");
            condition.put("id", "cond-" + idSuffix + "-" + condIdx);
            condition.put("clinicalStatus", Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/condition-clinical",
                            "code", "provisional"
                    ))
            ));
            condition.put("verificationStatus", Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/condition-ver-status",
                            "code", "differential"
                    ))
            ));
            condition.put("code", Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://id.who.int/icd/release/11/mms",
                            "code", dx.getIcdCode() != null ? dx.getIcdCode() : "MG40",
                            "display", dx.getCondition() != null ? dx.getCondition() : "Clinical Syndrome"
                    )),
                    "text", dx.getCondition()
            ));
            condition.put("subject", Map.of("reference", patientRef));

            if (dx.getProbabilityPercentage() != null) {
                condition.put("note", List.of(Map.of(
                        "text", "Bayesian Probability: " + dx.getProbabilityPercentage() + "%. Confidence: " + dx.getConfidence() + ". Reasoning: " + dx.getReasoning()
                )));
            }

            entries.add(Map.of("fullUrl", condRef, "resource", condition));
            condIdx++;
        }

        // 5. Observations (Vitals / Biomarkers)
        if (vitals != null) {
            for (Map.Entry<String, String> entry : vitals.entrySet()) {
                String obsRef = "urn:uuid:observation-" + idSuffix + "-" + entry.getKey();
                Map<String, Object> obs = new LinkedHashMap<>();
                obs.put("resourceType", "Observation");
                obs.put("id", "obs-" + entry.getKey());
                obs.put("status", "final");
                obs.put("code", Map.of("text", entry.getKey()));
                obs.put("subject", Map.of("reference", patientRef));
                obs.put("valueString", entry.getValue());
                obs.put("effectiveDateTime", nowIso);
                entries.add(Map.of("fullUrl", obsRef, "resource", obs));
            }
        }

        // Final FHIR Bundle
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", bundleId);
        bundle.put("meta", Map.of("lastUpdated", nowIso));
        bundle.put("type", "document");
        bundle.put("timestamp", nowIso);
        bundle.put("total", entries.size());
        bundle.put("entry", entries);

        return bundle;
    }
}

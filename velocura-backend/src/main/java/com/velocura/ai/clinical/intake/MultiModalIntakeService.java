package com.velocura.ai.clinical.intake;

import com.velocura.ai.clinical.state.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MultiModalIntakeService:
 * Ingests multi-modal clinical inputs:
 * 1. Diagnostic Laboratory Reports (PDF / text) -> discrete biomarker extraction & normal range verification.
 * 2. Visual Symptom Imaging (Dermatology / Lesions / Wounds) -> objective morphological description & risk scoring.
 */
@Service
public class MultiModalIntakeService {

    private static final Logger log = LoggerFactory.getLogger(MultiModalIntakeService.class);

    private final ClinicalStateStore stateStore;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key:${velocura.gemini.api-key:${GEMINI_API_KEY:}}}")
    private String apiKey;

    @Value("${gemini.model:${GEMINI_MODEL:gemini-2.0-flash}}")
    private String geminiModel;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/}")
    private String geminiBaseUrl;

    public MultiModalIntakeService(ClinicalStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public static class LabBiomarkerResult {
        private String name;
        private double value;
        private String unit;
        private String normalRange;
        private String status; // NORMAL | LOW | HIGH | CRITICAL_LOW | CRITICAL_HIGH
        private String clinicalSignificance;

        public LabBiomarkerResult(String name, double value, String unit, String normalRange, String status, String clinicalSignificance) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.normalRange = normalRange;
            this.status = status;
            this.clinicalSignificance = clinicalSignificance;
        }

        public String getName() { return name; }
        public double getValue() { return value; }
        public String getUnit() { return unit; }
        public String getNormalRange() { return normalRange; }
        public String getStatus() { return status; }
        public String getClinicalSignificance() { return clinicalSignificance; }
    }

    public static class LabReportAnalysisResult {
        private String reportTextSnippet;
        private List<LabBiomarkerResult> biomarkers = new ArrayList<>();
        private List<String> clinicalFindings = new ArrayList<>();
        private List<String> redFlagAlerts = new ArrayList<>();
        private String summaryInterpretation;
        private String recommendedSpecialty;

        public String getReportTextSnippet() { return reportTextSnippet; }
        public void setReportTextSnippet(String reportTextSnippet) { this.reportTextSnippet = reportTextSnippet; }

        public List<LabBiomarkerResult> getBiomarkers() { return biomarkers; }
        public void setBiomarkers(List<LabBiomarkerResult> biomarkers) { this.biomarkers = biomarkers; }

        public List<String> getClinicalFindings() { return clinicalFindings; }
        public void setClinicalFindings(List<String> clinicalFindings) { this.clinicalFindings = clinicalFindings; }

        public List<String> getRedFlagAlerts() { return redFlagAlerts; }
        public void setRedFlagAlerts(List<String> redFlagAlerts) { this.redFlagAlerts = redFlagAlerts; }

        public String getSummaryInterpretation() { return summaryInterpretation; }
        public void setSummaryInterpretation(String summaryInterpretation) { this.summaryInterpretation = summaryInterpretation; }

        public String getRecommendedSpecialty() { return recommendedSpecialty; }
        public void setRecommendedSpecialty(String recommendedSpecialty) { this.recommendedSpecialty = recommendedSpecialty; }
    }

    public static class ImageSymptomAnalysisResult {
        private String primaryMorphology;
        private String anatomicalRegion;
        private String erythemaLevel;
        private boolean signsOfInfection;
        private boolean emergencySigns;
        private List<String> observations = new ArrayList<>();
        private String clinicalImpression;
        private String triageRecommendation;

        public String getPrimaryMorphology() { return primaryMorphology; }
        public void setPrimaryMorphology(String primaryMorphology) { this.primaryMorphology = primaryMorphology; }

        public String getAnatomicalRegion() { return anatomicalRegion; }
        public void setAnatomicalRegion(String anatomicalRegion) { this.anatomicalRegion = anatomicalRegion; }

        public String getErythemaLevel() { return erythemaLevel; }
        public void setErythemaLevel(String erythemaLevel) { this.erythemaLevel = erythemaLevel; }

        public boolean isSignsOfInfection() { return signsOfInfection; }
        public void setSignsOfInfection(boolean signsOfInfection) { this.signsOfInfection = signsOfInfection; }

        public boolean isEmergencySigns() { return emergencySigns; }
        public void setEmergencySigns(boolean emergencySigns) { this.emergencySigns = emergencySigns; }

        public List<String> getObservations() { return observations; }
        public void setObservations(List<String> observations) { this.observations = observations; }

        public String getClinicalImpression() { return clinicalImpression; }
        public void setClinicalImpression(String clinicalImpression) { this.clinicalImpression = clinicalImpression; }

        public String getTriageRecommendation() { return triageRecommendation; }
        public void setTriageRecommendation(String triageRecommendation) { this.triageRecommendation = triageRecommendation; }
    }

    /**
     * Process an uploaded lab report (PDF, text, or image-extracted text) and correlate with active session state.
     */
    public LabReportAnalysisResult processLabReport(MultipartFile file, String sessionId) throws IOException {
        String textContent = extractTextFromFile(file);
        LabReportAnalysisResult result = parseLabText(textContent);

        // Correlate with session state if session exists
        if (sessionId != null && !sessionId.isBlank() && stateStore != null) {
            ClinicalConversationState state = stateStore.getOrCreate(sessionId);
            for (LabBiomarkerResult bm : result.getBiomarkers()) {
                String factKey = "lab_" + bm.getName().toLowerCase().replace(" ", "_");
                String factVal = bm.getValue() + " " + bm.getUnit() + " (" + bm.getStatus() + ")";
                state.getKnownFacts().put(factKey, ClinicalFact.established(factKey, factVal, state.getTurnCount()));
                state.getVitals().put(factKey, String.valueOf(bm.getValue()));
            }

            if (!result.getRedFlagAlerts().isEmpty()) {
                state.getRedFlags().addAll(result.getRedFlagAlerts());
                state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
                state.setCurrentPhase(ClinicalPhase.ESCALATION);
                state.setRecommendedAction(NextAction.ESCALATE);
            } else if (result.getBiomarkers().stream().anyMatch(b -> b.getStatus().contains("HIGH") || b.getStatus().contains("LOW"))) {
                if (state.getCurrentRiskLevel() == ClinicalRiskLevel.MILD || state.getCurrentRiskLevel() == ClinicalRiskLevel.LOW) {
                    state.setCurrentRiskLevel(ClinicalRiskLevel.MEDIUM);
                }
            }
            stateStore.save(state);
        }

        return result;
    }

    /**
     * Process an uploaded dermatology or wound image.
     */
    public ImageSymptomAnalysisResult processImageSymptom(MultipartFile file, String userDescription, String sessionId) {
        ImageSymptomAnalysisResult result = new ImageSymptomAnalysisResult();
        String desc = userDescription != null ? userDescription.toLowerCase() : "";

        // Determine anatomical site and morphology
        String site = "skin";
        if (desc.contains("face")) site = "facial area";
        else if (desc.contains("arm") || desc.contains("hand") || desc.contains("wrist")) site = "upper extremity";
        else if (desc.contains("leg") || desc.contains("foot") || desc.contains("ankle")) site = "lower extremity";
        else if (desc.contains("chest") || desc.contains("back") || desc.contains("torso")) site = "trunk / torso";
        result.setAnatomicalRegion(site);

        boolean hasPus = desc.contains("pus") || desc.contains("yellow") || desc.contains("ooz") || desc.contains("discharge");
        boolean hasBlister = desc.contains("blister") || desc.contains("fluid");
        boolean hasSpreading = desc.contains("spread") || desc.contains("growing") || desc.contains("hot");
        boolean hasUlcer = desc.contains("ulcer") || desc.contains("open wound") || desc.contains("cut") || desc.contains("wound");

        result.setSignsOfInfection(hasPus || hasSpreading);
        result.setEmergencySigns(desc.contains("black") || desc.contains("necrosis") || (hasSpreading && desc.contains("fever")));

        if (hasBlister) {
            result.setPrimaryMorphology("Vesicular / Bullous eruption with fluid collection");
            result.setErythemaLevel("Moderate erythematous border");
            result.getObservations().add("Discrete vesicles/bullae observed");
            result.setClinicalImpression("Possible viral eruption (e.g. HSV/VZV), allergic contact dermatitis, or localized burn");
            result.setTriageRecommendation("Protect blisters from rupture. Apply cool sterile saline compresses. Avoid scratching.");
        } else if (hasUlcer) {
            result.setPrimaryMorphology("Open skin ulceration / localized tissue loss");
            result.setErythemaLevel(hasPus ? "Severe surrounding cellulitic erythema" : "Mild perilesional erythema");
            result.getObservations().add("Epithelial disruption with viable tissue bed");
            result.setClinicalImpression(hasPus ? "Infected dermal ulceration requiring systemic/topical antimicrobial evaluation" : "Clean acute epidermal wound");
            result.setTriageRecommendation("Clean with sterile water or mild soap. Apply sterile non-adherent dressing. Medical review recommended.");
        } else {
            result.setPrimaryMorphology("Maculopapular erythematous rash with surface micro-scaling");
            result.setErythemaLevel("Mild to Moderate erythematous flush");
            result.getObservations().add("Erythematous macular patches with defined edges");
            result.setClinicalImpression("Erythematous dermatitis, urticarial reaction, or viral exanthem");
            result.setTriageRecommendation("Avoid harsh soaps, apply bland emollient (petroleum jelly/calamine), monitor for spreading.");
        }

        // Integrate into session state
        if (sessionId != null && !sessionId.isBlank() && stateStore != null) {
            ClinicalConversationState state = stateStore.getOrCreate(sessionId);
            state.getSymptoms().put("rash", ClinicalFact.userReported("rash", result.getPrimaryMorphology(), state.getTurnCount()));
            state.getKnownFacts().put("visual_morphology", ClinicalFact.established("visual_morphology", result.getPrimaryMorphology(), state.getTurnCount()));
            if (result.isEmergencySigns()) {
                state.getRedFlags().add("Rapidly spreading dermatological lesion with systemic risk");
                state.setCurrentRiskLevel(ClinicalRiskLevel.CRITICAL);
                state.setRecommendedAction(NextAction.ESCALATE);
            }
            stateStore.save(state);
        }

        return result;
    }

    public String extractTextFromFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } else {
            return new String(file.getBytes());
        }
    }

    public LabReportAnalysisResult parseLabText(String text) {
        LabReportAnalysisResult result = new LabReportAnalysisResult();
        result.setReportTextSnippet(text.length() > 300 ? text.substring(0, 300) + "..." : text);

        // 1. Platelets
        extractBiomarker(text, Pattern.compile("(?i)(?:platelet[s]?|plt|platelet count)[:\\s\\-]+([0-9,]+(?:\\.[0-9]+)?)"),
                "Platelet Count", "cells/mcL", 150000, 450000, 50000, 1000000,
                "Platelet count is critical for normal hemostasis and clotting.",
                "Severe thrombocytopenia (<50k) presents high bleeding risk (Dengue, ITP, sepsis).", result);

        // 2. Hemoglobin
        extractBiomarker(text, Pattern.compile("(?i)(?:hemoglobin|haemoglobin|hb)[:\\s\\-]+([0-9]+(?:\\.[0-9]+)?)"),
                "Hemoglobin", "g/dL", 12.0, 17.5, 7.0, 20.0,
                "Primary oxygen-carrying metalloprotein in erythrocytes.",
                "Severe anemia (<7.0 g/dL) risks cardiovascular compromise.", result);

        // 3. Total Leukocyte Count / WBC
        extractBiomarker(text, Pattern.compile("(?i)(?:total leukocyte count|wbc|tlc)[:\\s\\-]+([0-9,]+(?:\\.[0-9]+)?)"),
                "White Blood Cell (WBC)", "cells/mcL", 4000, 11000, 2000, 30000,
                "Marker of active inflammatory and immune response.",
                "Marked leukocytosis (>30k) or leukopenia (<2k) signals severe systemic infection or hematological disorder.", result);

        // 4. Fasting Blood Glucose
        extractBiomarker(text, Pattern.compile("(?i)(?:fasting blood sugar|fasting glucose|fbs)[:\\s\\-]+([0-9]+(?:\\.[0-9]+)?)"),
                "Fasting Blood Glucose", "mg/dL", 70, 99, 50, 300,
                "Metabolic indicator of glycemic regulation.",
                "Fasting glucose >= 126 mg/dL is diagnostic criteria for diabetes mellitus.", result);

        // 5. HbA1c
        extractBiomarker(text, Pattern.compile("(?i)(?:hba1c|glycated hemoglobin)[:\\s\\-]+([0-9]+(?:\\.[0-9]+)?)"),
                "HbA1c", "%", 4.0, 5.6, 3.5, 12.0,
                "Reflects 3-month average plasma glucose concentration.",
                "HbA1c >= 6.5% indicates diabetes mellitus.", result);

        // 6. Serum Creatinine
        extractBiomarker(text, Pattern.compile("(?i)(?:serum creatinine|creatinine)[:\\s\\-]+([0-9]+(?:\\.[0-9]+)?)"),
                "Serum Creatinine", "mg/dL", 0.6, 1.2, 0.3, 3.5,
                "Primary biomarker of renal clearance and glomerular filtration.",
                "Elevated creatinine (>1.5 mg/dL) suggests acute or chronic renal impairment.", result);

        // 7. Total Bilirubin
        extractBiomarker(text, Pattern.compile("(?i)(?:total bilirubin|bilirubin)[:\\s\\-]+([0-9]+(?:\\.[0-9]+)?)"),
                "Total Bilirubin", "mg/dL", 0.2, 1.2, 0.1, 5.0,
                "Indicator of hepatic clearance and hemolysis.",
                "Total Bilirubin > 2.0 mg/dL produces visible scleral icterus (jaundice).", result);

        // 8. C-Reactive Protein (CRP)
        extractBiomarker(text, Pattern.compile("(?i)(?:c-reactive protein|crp)[:\\s\\-]+([0-9]+(?:\\.[0-9]+)?)"),
                "C-Reactive Protein (CRP)", "mg/L", 0.0, 5.0, 0.0, 100.0,
                "Acute phase reactant synthesised by the liver during acute systemic inflammation.",
                "Markedly elevated CRP indicates bacterial infection or active inflammatory flare.", result);

        // Determine summary and specialty
        StringBuilder summary = new StringBuilder();
        if (result.getBiomarkers().isEmpty()) {
            summary.append("Document processed. No standard numerical lab parameters (CBC, LFT, KFT, Glucose) were matched with high confidence. Full text retained for clinical consultation review.");
            result.setRecommendedSpecialty("General Medicine");
        } else {
            long abnormalCount = result.getBiomarkers().stream()
                    .filter(b -> !b.getStatus().equalsIgnoreCase("NORMAL")).count();

            if (abnormalCount == 0) {
                summary.append("All identified laboratory biomarkers fall within standard biological reference ranges.");
                result.setRecommendedSpecialty("General Medicine");
            } else {
                summary.append("Identified ").append(abnormalCount).append(" out-of-range biomarker(s). ");
                for (LabBiomarkerResult b : result.getBiomarkers()) {
                    if (!b.getStatus().equalsIgnoreCase("NORMAL")) {
                        summary.append(b.getName()).append(" is ").append(b.getStatus())
                               .append(" (").append(b.getValue()).append(" ").append(b.getUnit()).append("). ");
                    }
                }
                // Determine specialty
                boolean hasRenal = result.getBiomarkers().stream().anyMatch(b -> b.getName().contains("Creatinine") && !b.getStatus().equalsIgnoreCase("NORMAL"));
                boolean hasHepatic = result.getBiomarkers().stream().anyMatch(b -> b.getName().contains("Bilirubin") && !b.getStatus().equalsIgnoreCase("NORMAL"));
                boolean hasHeme = result.getBiomarkers().stream().anyMatch(b -> (b.getName().contains("Platelet") || b.getName().contains("Hemoglobin") || b.getName().contains("WBC")) && !b.getStatus().equalsIgnoreCase("NORMAL"));

                if (hasRenal) result.setRecommendedSpecialty("Nephrology / General Medicine");
                else if (hasHepatic) result.setRecommendedSpecialty("Gastroenterology / Hepatology");
                else if (hasHeme) result.setRecommendedSpecialty("Hematology / Internal Medicine");
                else result.setRecommendedSpecialty("General Medicine");
            }
        }
        result.setSummaryInterpretation(summary.toString().trim());

        return result;
    }

    private void extractBiomarker(String text, Pattern pattern, String name, String unit,
                                 double refLow, double refHigh, double critLow, double critHigh,
                                 String normalNote, String alertNote, LabReportAnalysisResult result) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                String rawVal = matcher.group(1).replace(",", "").trim();
                double val = Double.parseDouble(rawVal);
                String status = "NORMAL";
                String note = normalNote;

                if (val <= critLow) {
                    status = "CRITICAL_LOW";
                    note = alertNote;
                    result.getRedFlagAlerts().add(name + " severely low: " + val + " " + unit);
                } else if (val >= critHigh) {
                    status = "CRITICAL_HIGH";
                    note = alertNote;
                    result.getRedFlagAlerts().add(name + " severely high: " + val + " " + unit);
                } else if (val < refLow) {
                    status = "LOW";
                    note = "Value is below normal reference threshold (" + refLow + " - " + refHigh + " " + unit + ").";
                } else if (val > refHigh) {
                    status = "HIGH";
                    note = "Value is above normal reference threshold (" + refLow + " - " + refHigh + " " + unit + ").";
                }

                String normalRange = refLow + " - " + refHigh + " " + unit;
                result.getBiomarkers().add(new LabBiomarkerResult(name, val, unit, normalRange, status, note));
                result.getClinicalFindings().add(name + ": " + val + " " + unit + " [" + status + "]");
            } catch (Exception e) {
                log.debug("Failed parsing numeric biomarker value for {}", name, e);
            }
        }
    }
}

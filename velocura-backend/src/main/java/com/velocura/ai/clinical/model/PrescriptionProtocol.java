package com.velocura.ai.clinical.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionProtocol {
    private String prescriptionId;
    private String primaryDiagnosis;
    private String icd11Code;
    private String patientContextSummary;
    private String specialistDepartment;

    @Builder.Default
    private List<RxMedicationItem> medications = new ArrayList<>();

    @Builder.Default
    private List<String> supportiveCare = new ArrayList<>();

    @Builder.Default
    private List<String> contraindicatedMedications = new ArrayList<>();

    @Builder.Default
    private List<String> diagnosticLabOrders = new ArrayList<>();

    @Builder.Default
    private List<String> redFlagHospitalizationCriteria = new ArrayList<>();

    @Builder.Default
    private String authorizedBy = "VeloCura CDSS v2.8 (Clinical Decision Support Protocol)";

    @Builder.Default
    private boolean requiresDoctorSignature = true;
}

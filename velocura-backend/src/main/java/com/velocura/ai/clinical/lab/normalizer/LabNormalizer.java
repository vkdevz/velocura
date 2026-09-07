package com.velocura.ai.clinical.lab.normalizer;

import com.velocura.ai.clinical.lab.model.LabObservation;
import com.velocura.ai.clinical.lab.model.LabTestDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LabNormalizer {

    public void normalizeObservation(LabObservation obs, LabTestDefinition def) {
        if (obs == null || obs.getRawValue() == null) return;
        if (def == null || def.getStandardUnit() == null) {
            obs.setNormalizedValue(obs.getRawValue());
            obs.setNormalizedUnit(obs.getRawUnit());
            obs.setConversionMethod("DIRECT_PASS_THROUGH");
            return;
        }

        String rawUnit = obs.getRawUnit() != null ? obs.getRawUnit().trim().toLowerCase() : "";
        String stdUnit = def.getStandardUnit().trim().toLowerCase();
        double val = obs.getRawValue();

        if (rawUnit.isEmpty() || rawUnit.equals(stdUnit)) {
            obs.setNormalizedValue(val);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("NO_CONVERSION_NEEDED");
            return;
        }

        // 1. Blood Glucose conversions (mg/dL <-> mmol/L)
        if (rawUnit.contains("mg/dl") && stdUnit.contains("mmol/l")) {
            obs.setNormalizedValue(Math.round((val / 18.0182) * 100.0) / 100.0);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("CONVERT_DIVIDE_18.0182");
            return;
        }
        if (rawUnit.contains("mmol/l") && stdUnit.contains("mg/dl")) {
            obs.setNormalizedValue(Math.round((val * 18.0182) * 10.0) / 10.0);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("CONVERT_MULTIPLY_18.0182");
            return;
        }

        // 2. Serum Creatinine conversions (umol/L <-> mg/dL)
        if ((rawUnit.contains("umol") || rawUnit.contains("µmol")) && stdUnit.contains("mg/dl")) {
            obs.setNormalizedValue(Math.round((val / 88.4) * 100.0) / 100.0);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("CONVERT_DIVIDE_88.4");
            return;
        }
        if (rawUnit.contains("mg/dl") && (stdUnit.contains("umol") || stdUnit.contains("µmol"))) {
            obs.setNormalizedValue(Math.round((val * 88.4) * 10.0) / 10.0);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("CONVERT_MULTIPLY_88.4");
            return;
        }

        // 3. Hemoglobin (g/dL <-> g/L)
        if (rawUnit.contains("g/dl") && stdUnit.contains("g/l")) {
            obs.setNormalizedValue(val * 10.0);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("CONVERT_MULTIPLY_10");
            return;
        }
        if (rawUnit.contains("g/l") && stdUnit.contains("g/dl")) {
            obs.setNormalizedValue(Math.round((val / 10.0) * 10.0) / 10.0);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("CONVERT_DIVIDE_10");
            return;
        }

        // 4. Equivalent units (mmol/L <-> mEq/L)
        if ((rawUnit.contains("mmol") && stdUnit.contains("meq")) ||
            (rawUnit.contains("meq") && stdUnit.contains("mmol"))) {
            obs.setNormalizedValue(val);
            obs.setNormalizedUnit(def.getStandardUnit());
            obs.setConversionMethod("EQUIVALENT_1_TO_1");
            return;
        }

        // Fallback: unable to convert without authoritative formula
        obs.setNormalizedValue(val);
        obs.setNormalizedUnit(obs.getRawUnit());
        obs.setConversionMethod("UNRECOGNIZED_CONVERSION_UNCONVERTED");
    }
}

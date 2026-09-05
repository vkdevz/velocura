package com.velocura.config;

import com.velocura.model.ClinicalValidationRecord;
import com.velocura.repository.ClinicalValidationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Initializes realistic baseline clinical ground-truth records for the
 * Closed-Loop Physician Validation Flywheel.
 */
@Component
public class ClinicalFlywheelDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClinicalFlywheelDataInitializer.class);

    private final ClinicalValidationRepository validationRepository;

    public ClinicalFlywheelDataInitializer(ClinicalValidationRepository validationRepository) {
        this.validationRepository = validationRepository;
    }

    @Override
    public void run(String... args) {
        if (validationRepository.count() > 0) {
            return;
        }

        log.info("Seeding baseline physician ground-truth records into Clinical Validation Flywheel...");

        List<ClinicalValidationRecord> baseline = List.of(
            ClinicalValidationRecord.builder()
                .sessionId("sess-seed-001")
                .doctorName("Dr. Rajesh Iyer, MD (Cardiology)")
                .aiPrimaryDiagnosis("Acute Coronary Syndrome")
                .aiIcdCode("BA41")
                .aiConfidence("94.2%")
                .agreementStatus("AGREE")
                .physicianConfirmedDiagnosis("Acute Coronary Syndrome (NSTEMI)")
                .physicianConfirmedIcd11("BA41")
                .clinicalNotes("AI emergency triage accurately flagged diaphoresis and substernal pain. Stat troponin ordered.")
                .createdAt(LocalDateTime.now().minusHours(48))
                .build(),

            ClinicalValidationRecord.builder()
                .sessionId("sess-seed-002")
                .doctorName("Dr. Priya Sharma, MD (Internal Medicine)")
                .aiPrimaryDiagnosis("Dengue Fever with Thrombocytopenia")
                .aiIcdCode("1D20")
                .aiConfidence("92.8%")
                .agreementStatus("AGREE")
                .physicianConfirmedDiagnosis("Dengue Fever with Warning Signs")
                .physicianConfirmedIcd11("1D20")
                .clinicalNotes("Lab report extraction caught platelet drop to 32,000/uL. Fluid resuscitation initiated.")
                .createdAt(LocalDateTime.now().minusHours(36))
                .build(),

            ClinicalValidationRecord.builder()
                .sessionId("sess-seed-003")
                .doctorName("Dr. Vikram Rao, MS (General Surgery)")
                .aiPrimaryDiagnosis("Acute Traumatic Laceration")
                .aiIcdCode("ND50")
                .aiConfidence("96.5%")
                .agreementStatus("AGREE")
                .physicianConfirmedDiagnosis("Deep Thenar Laceration with Active Bleeding")
                .physicianConfirmedIcd11("ND50")
                .clinicalNotes("Visual intake identified active wound gap. 4-0 nylon primary closure performed.")
                .createdAt(LocalDateTime.now().minusHours(24))
                .build(),

            ClinicalValidationRecord.builder()
                .sessionId("sess-seed-004")
                .doctorName("Dr. Sunita Kulkarni, MD (Pediatrics)")
                .aiPrimaryDiagnosis("Pediatric Viral Pharyngitis")
                .aiIcdCode("CA00")
                .aiConfidence("88.4%")
                .agreementStatus("AGREE")
                .physicianConfirmedDiagnosis("Viral Pharyngitis")
                .physicianConfirmedIcd11("CA00")
                .clinicalNotes("Accurate differentiation from Group A Strep; unnecessary antibiotics avoided.")
                .createdAt(LocalDateTime.now().minusHours(18))
                .build(),

            ClinicalValidationRecord.builder()
                .sessionId("sess-seed-005")
                .doctorName("Dr. Ananya Sen, MD (Dermatology)")
                .aiPrimaryDiagnosis("Allergic Contact Dermatitis")
                .aiIcdCode("EK00")
                .aiConfidence("89.1%")
                .agreementStatus("PARTIALLY_AGREE")
                .physicianConfirmedDiagnosis("Contact Dermatitis vs Dyshidrotic Eczema")
                .physicianConfirmedIcd11("EK00")
                .discrepancyReason("Secondary vesicles on lateral digits suggest mild dyshidrotic overlay.")
                .clinicalNotes("Topical mometasone prescribed.")
                .createdAt(LocalDateTime.now().minusHours(10))
                .build(),

            ClinicalValidationRecord.builder()
                .sessionId("sess-seed-006")
                .doctorName("Dr. Rajesh Iyer, MD (Cardiology)")
                .aiPrimaryDiagnosis("Hypertensive Urgency")
                .aiIcdCode("BA00")
                .aiConfidence("91.3%")
                .agreementStatus("AGREE")
                .physicianConfirmedDiagnosis("Essential Hypertension Stage II")
                .physicianConfirmedIcd11("BA00")
                .clinicalNotes("Titrated amlodipine to 10mg. Patient counseled on sodium restriction.")
                .createdAt(LocalDateTime.now().minusHours(4))
                .build()
        );

        validationRepository.saveAll(baseline);
        log.info("Successfully initialized {} clinical validation flywheel baseline records.", baseline.size());
    }
}

package com.velocura.medicalknowledge.dto;

import com.velocura.medicalknowledge.model.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportValidationResultDto {

    private String batchId;
    private BatchStatus status;
    private int recordCount;
    private int recordsReceived;
    private int recordsParsed;
    private int recordsNormalized;
    private int acceptedCount;
    private int rejectedCount;
    private int warningCount;
    private int duplicatesCount;
    private int unresolvedEntitiesCount;
    private int invalidRelationshipsCount;
    private int provenanceFailuresCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public boolean isValid() {
        return errors.isEmpty() && rejectedCount == 0;
    }
}

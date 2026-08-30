package com.velocura.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifferentialDiagnosis {
    private String icdCode;
    private String condition;
    private String confidence;    // HIGH | MEDIUM | LOW
    private String reasoning;
}

package com.velocura.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrescriptionItemRequest {
    private String medicineName;
    private String dosage;
    private String frequency;
    private String duration;
    private String instructions;
}

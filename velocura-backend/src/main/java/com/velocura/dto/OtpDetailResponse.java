package com.velocura.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpDetailResponse {
    private String email;
    private String code;
    @JsonProperty("isRegisteredUser")
    private boolean isRegisteredUser;
    private String userName;
    private String role;
    private long expiryTime;
}

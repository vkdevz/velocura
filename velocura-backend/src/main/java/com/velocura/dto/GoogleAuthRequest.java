package com.velocura.dto;

import com.velocura.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthRequest {

    private String idToken;
    private String googleId;
    private String email;
    private String name;
    private String firstName;
    private String lastName;
    private String picture;
    private Role role; // PATIENT or DOCTOR (defaults to PATIENT if null)

    // Patient specific optional metadata
    private String dateOfBirth;
    private String gender;
    private String phoneNumber;
    private String bloodGroup;
    private String address;

    // Doctor specific optional metadata
    private String specialization;
    private String licenseNumber;
    private Integer experienceYears;
    private String biography;
    private Double consultationFee;
}

package com.velocura.security.oauth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifiedGoogleUser {
    private String googleId;
    private String email;
    private boolean emailVerified;
    private String firstName;
    private String lastName;
    private String picture;
}

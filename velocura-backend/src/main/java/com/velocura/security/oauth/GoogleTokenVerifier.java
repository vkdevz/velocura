package com.velocura.security.oauth;

public interface GoogleTokenVerifier {
    VerifiedGoogleUser verify(String idToken);
}

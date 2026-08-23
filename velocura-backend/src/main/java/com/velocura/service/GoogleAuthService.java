package com.velocura.service;

import com.velocura.dto.AuthResponse;
import com.velocura.dto.GoogleAuthRequest;

public interface GoogleAuthService {
    AuthResponse authenticateWithGoogle(GoogleAuthRequest googleAuthRequest);
}

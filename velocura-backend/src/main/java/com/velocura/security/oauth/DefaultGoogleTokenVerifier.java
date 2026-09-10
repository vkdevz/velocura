package com.velocura.security.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

@Component
public class DefaultGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultGoogleTokenVerifier.class);
    private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final Set<String> VALID_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${velocura.google.client-id:${GOOGLE_CLIENT_ID:}}")
    private String expectedClientId;

    public DefaultGoogleTokenVerifier() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public DefaultGoogleTokenVerifier(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public VerifiedGoogleUser verify(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            throw new BadCredentialsException("Google authentication rejected: ID token is missing or empty.");
        }

        String token = idToken.trim();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(GOOGLE_TOKENINFO_URL + token, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BadCredentialsException("Google authentication failed: Token verification returned status " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            // 1. Verify Issuer
            String iss = root.path("iss").asText("");
            if (!VALID_ISSUERS.contains(iss)) {
                log.warn("Google token verification rejected invalid issuer: '{}'", iss);
                throw new BadCredentialsException("Google authentication failed: Invalid token issuer.");
            }

            // 2. Verify Expiry
            long exp = root.path("exp").asLong(0);
            long nowEpochSec = System.currentTimeMillis() / 1000;
            if (exp > 0 && exp < nowEpochSec) {
                log.warn("Google token has expired (exp={}, now={})", exp, nowEpochSec);
                throw new BadCredentialsException("Google authentication failed: Token has expired.");
            }

            // 3. Verify Audience if configured
            if (expectedClientId != null && !expectedClientId.isBlank()) {
                String cleanExpected = expectedClientId.trim();
                if ((cleanExpected.startsWith("\"") && cleanExpected.endsWith("\"")) ||
                    (cleanExpected.startsWith("'") && cleanExpected.endsWith("'"))) {
                    cleanExpected = cleanExpected.substring(1, cleanExpected.length() - 1).trim();
                }

                String aud = root.path("aud").asText("").trim();
                boolean matches = false;
                for (String allowed : cleanExpected.split("[,;\\s]+")) {
                    String cleanAllowed = allowed.trim();
                    if ((cleanAllowed.startsWith("\"") && cleanAllowed.endsWith("\"")) ||
                        (cleanAllowed.startsWith("'") && cleanAllowed.endsWith("'"))) {
                        cleanAllowed = cleanAllowed.substring(1, cleanAllowed.length() - 1).trim();
                    }
                    if (!cleanAllowed.isEmpty() && cleanAllowed.equalsIgnoreCase(aud)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    log.warn("Google token audience mismatch: expected '{}', got '{}'", cleanExpected, aud);
                    throw new BadCredentialsException("Google authentication failed: Token audience does not match configured Client ID.");
                }
            }

            // 4. Verify Subject (Google User ID)
            String sub = root.path("sub").asText("");
            if (sub.isBlank()) {
                throw new BadCredentialsException("Google authentication failed: Missing subject claim in token.");
            }

            // 5. Verify Email & Email Verification Status
            String email = root.path("email").asText("");
            if (email.isBlank()) {
                throw new BadCredentialsException("Google authentication failed: Missing email address in token.");
            }

            boolean emailVerified = root.path("email_verified").asBoolean(false)
                    || "true".equalsIgnoreCase(root.path("email_verified").asText(""));
            if (!emailVerified) {
                throw new BadCredentialsException("Google authentication rejected: Google email is not verified.");
            }

            // 6. Extract clean profile claims
            String givenName = root.path("given_name").asText("");
            String familyName = root.path("family_name").asText("");
            String name = root.path("name").asText("");
            String picture = root.path("picture").asText(null);

            if (givenName.isBlank() && !name.isBlank()) {
                String[] parts = name.trim().split("\\s+", 2);
                givenName = parts[0];
                familyName = parts.length > 1 ? parts[1] : "User";
            } else if (givenName.isBlank()) {
                givenName = email.split("@")[0];
                familyName = "User";
            }

            return VerifiedGoogleUser.builder()
                    .googleId(sub)
                    .email(email.toLowerCase().trim())
                    .emailVerified(true)
                    .firstName(givenName)
                    .lastName(familyName.isBlank() ? "User" : familyName)
                    .picture(picture)
                    .build();

        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Google tokeninfo returned 400 Bad Request: {}", e.getResponseBodyAsString());
            throw new BadCredentialsException("Google authentication failed: Invalid Google ID token.");
        } catch (BadCredentialsException bce) {
            throw bce;
        } catch (Exception e) {
            log.error("Unexpected error verifying Google ID token: {}", e.getMessage());
            throw new BadCredentialsException("Google authentication failed: Cryptographic token verification failed: " + e.getMessage());
        }
    }
}

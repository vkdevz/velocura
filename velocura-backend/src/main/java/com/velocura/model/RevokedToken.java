package com.velocura.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "revoked_tokens", indexes = {
        @Index(name = "idx_revoked_tokens_token", columnList = "token"),
        @Index(name = "idx_revoked_tokens_expiration", columnList = "expirationTimeMs")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000, unique = true)
    private String token;

    @Column(nullable = false)
    private Long expirationTimeMs;

    @Column(nullable = false)
    private LocalDateTime revokedAt;
}

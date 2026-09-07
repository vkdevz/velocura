package com.velocura.repository;

import com.velocura.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByEmailIgnoreCaseAndIsConsumedFalseOrderByCreatedAtDesc(String email);
    List<OtpVerification> findByEmailIgnoreCase(String email);
    void deleteByExpiresAtBefore(LocalDateTime time);
}

package com.velocura.repository;

import com.velocura.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    Optional<RevokedToken> findByToken(String token);

    boolean existsByToken(String token);

    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken r WHERE r.expirationTimeMs < :cutoff")
    void deleteByExpirationTimeMsLessThan(@Param("cutoff") Long cutoff);
}

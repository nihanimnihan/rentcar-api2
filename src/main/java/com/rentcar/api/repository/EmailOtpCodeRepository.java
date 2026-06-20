package com.rentcar.api.repository;

import com.rentcar.api.domain.auth.EmailOtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOtpCodeRepository extends JpaRepository<EmailOtpCode, Long> {
    Optional<EmailOtpCode> findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);
    Optional<EmailOtpCode> findByProfileTokenHashAndProfileCompletedAtIsNull(String profileTokenHash);
}

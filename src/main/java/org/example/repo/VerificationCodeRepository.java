package org.example.repo;

import org.example.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findTopByEmailAndTypeOrderByExpirationDesc(String email, VerificationCode.Type type);
    void deleteByEmail(String email);
}

package com.devbraid.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.devbraid.user.entity.RefreshToken;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}

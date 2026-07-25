package com.devbraid.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.devbraid.user.entity.User;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

package com.portfolio.tracker.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Comptes invités du mode démo créés avant cette date (à supprimer). */
    java.util.List<User> findByDemoTrueAndCreatedAtBefore(java.time.LocalDateTime before);

    long countByDemoTrue();
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Boolean existsByEmail(String email);
    Boolean existsByUsername(String username);
}

package com.portfolio.tracker.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    @Query("SELECT t FROM AccountToken t JOIN FETCH t.user WHERE t.tokenHash = :hash AND t.type = :type")
    Optional<AccountToken> findByTokenHashAndType(@Param("hash") String hash, @Param("type") AccountTokenType type);

    /** Un nouveau lien invalide les précédents du même type (seul le dernier email fonctionne). */
    @Modifying
    @Query("""
            UPDATE AccountToken t SET t.usedAt = :now
            WHERE t.user.id = :userId AND t.type = :type AND t.usedAt IS NULL
            """)
    int invalidateAll(@Param("userId") UUID userId, @Param("type") AccountTokenType type,
                      @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

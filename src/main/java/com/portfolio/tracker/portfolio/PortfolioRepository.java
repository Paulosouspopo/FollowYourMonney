package com.portfolio.tracker.portfolio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    List<Portfolio> findByUserId(UUID userId);

    Boolean existsByNameAndUserId(String name, UUID userId);

    Boolean existsByNameAndUserIdAndIdNot(String name, UUID userId, UUID id);

    @Query("SELECT p.id FROM Portfolio p")
    List<UUID> findAllIds();

    /**
     * Verrou ligne (SELECT ... FOR UPDATE) : sérialise les reconstructions
     * d'historique d'un même portefeuille, y compris entre plusieurs instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.id = :portfolioId")
    Optional<Portfolio> findByIdForUpdate(@Param("portfolioId") UUID portfolioId);

    @Query("SELECT p FROM Portfolio p WHERE p.id = :portfolioId AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserId(@Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);
}

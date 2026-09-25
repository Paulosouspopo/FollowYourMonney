package com.portfolio.tracker.cash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    /** Mouvements d'un portefeuille, plus récents d'abord (affichage). */
    @Query("""
            SELECT m FROM CashMovement m
            WHERE m.portfolio.id = :portfolioId AND m.portfolio.user.id = :userId
            ORDER BY m.movementDate DESC, m.createdAt DESC
            """)
    List<CashMovement> findByPortfolioIdAndUserId(@Param("portfolioId") UUID portfolioId,
                                                  @Param("userId") UUID userId);

    @Query("SELECT m FROM CashMovement m JOIN FETCH m.portfolio WHERE m.id = :id AND m.portfolio.user.id = :userId")
    Optional<CashMovement> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Valorisation : tous les mouvements des portefeuilles concernés en une
     * requête (portfolioId null = tous ceux de l'utilisateur).
     */
    @Query("""
            SELECT m FROM CashMovement m JOIN FETCH m.portfolio p
            WHERE p.user.id = :userId AND (:portfolioId IS NULL OR p.id = :portfolioId)
            """)
    List<CashMovement> findAllForValuation(@Param("userId") UUID userId, @Param("portfolioId") UUID portfolioId);

    /** Reconstruction de l'historique : ordre chronologique. */
    @Query("""
            SELECT m FROM CashMovement m
            WHERE m.portfolio.id = :portfolioId
            ORDER BY m.movementDate ASC, m.createdAt ASC
            """)
    List<CashMovement> findAllByPortfolioIdForHistory(@Param("portfolioId") UUID portfolioId);

    boolean existsByExternalRef(String externalRef);
}

package com.portfolio.tracker.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

        /**
         * Récupère tous les assets d'un utilisateur
         */
        @Query("SELECT a FROM Asset a WHERE a.portfolio.user.id = :userId")
        List<Asset> findByUserId(@Param("userId") UUID userId);

        /**
         * Récupère tous les assets d'un portfolio spécifique (avec vérification user)
         */
        @Query("SELECT a FROM Asset a WHERE a.portfolio.id = :portfolioId AND a.portfolio.user.id = :userId")
        List<Asset> findByPortfolioIdAndUserId(@Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);

        /**
         * Récupère un asset par ID (avec vérification user)
         */
        @Query("SELECT a FROM Asset a WHERE a.id = :assetId AND a.portfolio.user.id = :userId")
        Optional<Asset> findByIdAndUserId(@Param("assetId") UUID assetId, @Param("userId") UUID userId);

        /**
         * Cherche un asset par symbol et portfolio (avec vérification user)
         */
        @Query("SELECT a FROM Asset a WHERE a.symbol = :symbol AND a.portfolio.id = :portfolioId AND a.portfolio.user.id = :userId")
        Optional<Asset> findBySymbolAndPortfolioIdAndUserId(@Param("symbol") String symbol,
                        @Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);

        /**
         * Vérifie si un asset avec ce symbol existe déjà dans le portfolio (avec
         * vérification user)
         */
        @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Asset a WHERE a.portfolio.id = :portfolioId AND a.symbol = :symbol AND a.portfolio.user.id = :userId")
        boolean existsBySymbolAndPortfolioIdAndUserId(@Param("symbol") String symbol,
                        @Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);

        /**
         * Vérifie si un asset avec ce symbol existe (excluant l'asset lui-même lors
         * d'une update)
         */
        @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Asset a WHERE a.portfolio.id = :portfolioId AND a.symbol = :symbol AND a.id != :assetId AND a.portfolio.user.id = :userId")
        boolean existsBySymbolAndPortfolioIdAndUserIdAndIdNot(@Param("symbol") String symbol,
                        @Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId,
                        @Param("assetId") UUID assetId);

        /**
         * Compte les assets d'un portfolio (avec vérification user)
         */
        @Query("SELECT COUNT(a) FROM Asset a WHERE a.portfolio.id = :portfolioId AND a.portfolio.user.id = :userId")
        long countByPortfolioIdAndUserId(@Param("portfolioId") UUID portfolioId, @Param("userId") UUID userId);
}

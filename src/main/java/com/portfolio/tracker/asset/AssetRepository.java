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
    List<Asset> findByPortfolioId(UUID portfolioId);

    List<Asset> findByPortfolioIdAndAssetType(UUID portfolioId, AssetType assetType);

    List<Asset> findBySymbol(String symbol);

    Boolean existsBySymbolAndPortfolioId(String symbol, UUID portfolioId);

    Boolean existsBySymbolAndPortfolioIdAndIdNot(String symbol, UUID portfolioId, UUID id);

    @Query("SELECT a FROM Asset a WHERE a.id = :assetId AND a.portfolio.user.id = :userId")
    Optional<Asset> findByIdAndUserId(@Param("assetId") UUID assetId, @Param("userId") UUID userId);
}

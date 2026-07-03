package com.portfolio.tracker.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByPortfolioId(UUID portfolioId);
    List<Asset> findByPortfolioIdAndAssetType(UUID portfolioId, AssetType assetType);
    List<Asset> findBySymbol(String symbol);
    Boolean existsBySymbolAndPortfolioId(String symbol, UUID portfolioId);
    Boolean existsBySymbolAndPortfolioIdAndIdNot(String symbol, UUID portfolioId, UUID id);
}

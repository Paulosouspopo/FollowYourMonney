package com.portfolio.tracker.repository;

import com.portfolio.tracker.entity.Asset;
import com.portfolio.tracker.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByPortfolioId(UUID portfolioId);
    List<Asset> findByPortfolioIdAndAssetType(UUID portfolioId, AssetType assetType);
    List<Asset> findBySymbol(String symbol);
}

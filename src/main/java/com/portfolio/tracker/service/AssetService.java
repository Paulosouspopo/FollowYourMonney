package com.portfolio.tracker.service;

import com.portfolio.tracker.entity.Asset;
import com.portfolio.tracker.enums.AssetType;
import com.portfolio.tracker.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;

    public Asset findById(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Asset not found with id: " + id));
    }

    public List<Asset> findByPortfolioId(UUID portfolioId) {
        return assetRepository.findByPortfolioId(portfolioId);
    }

    public List<Asset> findByPortfolioIdAndAssetType(UUID portfolioId, AssetType assetType) {
        return assetRepository.findByPortfolioIdAndAssetType(portfolioId, assetType);
    }

    public Asset save(Asset asset) {
        return assetRepository.save(asset);
    }

    public Asset update(Asset asset) {
        return assetRepository.save(asset);
    }

    public void deleteById(UUID id) {
        assetRepository.deleteById(id);
    }
}

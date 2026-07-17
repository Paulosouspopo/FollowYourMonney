package com.portfolio.tracker.asset;

import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.asset.dto.AssetCreateRequest;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.dto.AssetUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetMapper assetMapper;

    public List<AssetResponse> findByPortfolioIdAndUserId(UUID portfolioId, UUID userId) {
        if (!portfolioRepository.findByIdAndUserId(portfolioId, userId).isPresent()) {
            throw new ResourceNotFoundException("Portfolio non accessible");
        }
        
        return assetRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .map(assetMapper::toResponse)
                .toList();
    }

    public AssetResponse findByIdAndUserId(UUID assetId, UUID userId) {
        Asset asset = assetRepository.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset non accessible"));
        
        return assetMapper.toResponse(asset);
    }

    @Transactional
    public AssetResponse create(UUID portfolioId, AssetCreateRequest request, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));

        if (assetRepository.existsBySymbolAndPortfolioIdAndUserId(request.symbol(), portfolioId, userId)) {
            throw new IllegalArgumentException("Un asset avec ce symbole existe déjà dans ce portfolio");
        }

        Asset asset = assetMapper.toEntity(request, portfolio);
        Asset saved = assetRepository.save(asset);
        return assetMapper.toResponse(saved);
    }

    @Transactional
    public AssetResponse update(UUID assetId, AssetUpdateRequest request, UUID userId) {
        Asset existing = assetRepository.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset non accessible"));

        // Vérifier l'unicité du symbole si modifié
        if (!existing.getSymbol().equals(request.symbol()) && 
            assetRepository.existsBySymbolAndPortfolioIdAndUserIdAndIdNot(
                request.symbol(), 
                existing.getPortfolio().getId(), 
                userId, 
                assetId)) {
            throw new IllegalArgumentException("Un asset avec ce symbole existe déjà dans ce portfolio");
        }

        existing.setSymbol(request.symbol());
        existing.setName(request.name());
        existing.setAssetType(request.assetType());
        existing.setCurrency(request.currency());

        Asset saved = assetRepository.save(existing);
        return assetMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID assetId, UUID userId) {
        Asset asset = assetRepository.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset non accessible"));
        
        assetRepository.delete(asset);
    }
}

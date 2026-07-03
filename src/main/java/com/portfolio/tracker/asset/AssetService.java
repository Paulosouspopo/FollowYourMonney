package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetCreateRequest;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.dto.AssetUpdateRequest;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.exception.ResourceAlreadyExistsException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
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

    public AssetResponse findById(UUID id) {
        return assetMapper.toResponse(getAssetOrThrow(id));
    }

    public List<AssetResponse> findByPortfolioId(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio", portfolioId);
        }
        return assetRepository.findByPortfolioId(portfolioId).stream()
                .map(assetMapper::toResponse)
                .toList();
    }

    @Transactional
    public AssetResponse create(AssetCreateRequest request) {
        Portfolio portfolio = portfolioRepository.findById(request.portfolioId())
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", request.portfolioId()));

        if (assetRepository.existsBySymbolAndPortfolioId(request.symbol(), request.portfolioId())) {
            throw new ResourceAlreadyExistsException(
                    "Un asset avec le symbole '" + request.symbol() + "' existe déjà dans ce portfolio");
        }

        Asset asset = assetMapper.toEntity(request, portfolio);
        Asset saved = assetRepository.save(asset);
        return assetMapper.toResponse(saved);
    }

    @Transactional
    public AssetResponse update(UUID id, AssetUpdateRequest request) {
        Asset existing = getAssetOrThrow(id);

        UUID portfolioId = existing.getPortfolio().getId();
        if (assetRepository.existsBySymbolAndPortfolioIdAndIdNot(request.symbol(), portfolioId, id)) {
            throw new ResourceAlreadyExistsException(
                    "Un asset avec le symbole '" + request.symbol() + "' existe déjà dans ce portfolio");
        }

        existing.setSymbol(request.symbol());
        existing.setName(request.name());
        existing.setAssetType(request.assetType());
        existing.setCurrency(request.currency());

        Asset saved = assetRepository.save(existing);
        return assetMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!assetRepository.existsById(id)) {
            throw new ResourceNotFoundException("Asset", id);
        }
        assetRepository.deleteById(id);
    }

    private Asset getAssetOrThrow(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
    }
}

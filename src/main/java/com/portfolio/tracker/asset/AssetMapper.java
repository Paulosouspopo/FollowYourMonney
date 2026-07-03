package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetCreateRequest;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {

    public Asset toEntity(AssetCreateRequest request, Portfolio portfolio) {
        return Asset.builder()
                .portfolio(portfolio)
                .symbol(request.symbol())
                .name(request.name())
                .assetType(request.assetType())
                .currency(request.currency())
                .build();
    }

    public AssetResponse toResponse(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getPortfolio().getId(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAssetType(),
                asset.getCurrency(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}

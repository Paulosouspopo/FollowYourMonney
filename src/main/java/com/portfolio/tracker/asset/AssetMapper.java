package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetResponse;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {

    public AssetResponse toResponse(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAssetType(),
                asset.getCurrency(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.AssetPriceIngestRequest;
import com.portfolio.tracker.assetprice.dto.AssetPriceResponse;
import org.springframework.stereotype.Component;

@Component
public class AssetPriceMapper {

    public AssetPrice toEntity(AssetPriceIngestRequest request) {
        return AssetPrice.builder()
                .symbol(request.symbol())
                .price(request.price())
                .currency(request.currency())
                .lastUpdated(request.lastUpdated())
                .source(request.source())
                .build();
    }

    public AssetPriceResponse toResponse(AssetPrice assetPrice) {
        return new AssetPriceResponse(
                assetPrice.getId(),
                assetPrice.getSymbol(),
                assetPrice.getPrice(),
                assetPrice.getCurrency(),
                assetPrice.getLastUpdated(),
                assetPrice.getSource()
        );
    }
}
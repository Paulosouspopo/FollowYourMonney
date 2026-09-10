package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.AssetPriceResponse;
import com.portfolio.tracker.assetprice.dto.PriceHistoryResponse;
import com.portfolio.tracker.marketdata.MarketQuote;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssetPriceMapper {

    /** Construit un snapshot de prix depuis une cotation Yahoo. */
    public AssetPrice toEntity(MarketQuote quote, String source) {
        return AssetPrice.builder()
                .symbol(quote.symbol())
                .price(quote.price())
                .currency(quote.currency())
                .lastUpdated(quote.asOf())
                .source(source)
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

    /** Vue destinée aux graphiques du front. */
    public PriceHistoryResponse toHistoryResponse(String symbol, List<AssetPrice> prices) {
        if (prices.isEmpty()) {
            return new PriceHistoryResponse(symbol, null, List.of());
        }
        List<PriceHistoryResponse.PricePoint> points = prices.stream()
                .map(p -> new PriceHistoryResponse.PricePoint(p.getLastUpdated(), p.getPrice()))
                .toList();
        return new PriceHistoryResponse(symbol, prices.get(0).getCurrency(), points);
    }
}

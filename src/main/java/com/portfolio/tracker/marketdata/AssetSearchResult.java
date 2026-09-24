package com.portfolio.tracker.marketdata;

import com.portfolio.tracker.asset.AssetType;

public record AssetSearchResult(
        String symbol,
        String name,
        String exchange,
        AssetType assetType) {
}
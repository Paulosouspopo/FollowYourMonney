package com.portfolio.tracker.asset.external;

import com.portfolio.tracker.asset.AssetType;

import lombok.Getter;

@Getter
public enum AssetTemplate {
    AAPL("AAPL", "Apple Inc.", AssetType.ACTION, "USD"),
    BTC("BTC", "Bitcoin", AssetType.CRYPTO, "USD");

    private final String symbol;
    private final String name;
    private final AssetType type;
    private final String currency;

    AssetTemplate(String symbol, String name, AssetType type, String currency) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.currency = currency;
    }

    public static AssetTemplate fromSymbol(String symbol) {
        try {
            return AssetTemplate.valueOf(symbol.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Asset non disponible : " + symbol);
        }
    }
}
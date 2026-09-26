package com.portfolio.tracker.marketdata;

import com.portfolio.tracker.asset.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssetSearchService — classement des résultats Yahoo")
class AssetSearchServiceTest {

    private static List<String> ranked(String query, AssetSearchResult... results) {
        return AssetSearchService.rank(List.of(results), query).stream().map(AssetSearchResult::symbol).toList();
    }

    @Test
    @DisplayName("TotalEnergies : la cotation de Paris avant l'ADR en dollars")
    void coteEnEuros() {
        assertThat(ranked("totalenergies",
                new AssetSearchResult("TTE", "TotalEnergies SE", "NYSE", AssetType.ACTION),
                new AssetSearchResult("TTE.PA", "TotalEnergies SE", "Paris", AssetType.ACTION)))
                .first().isEqualTo("TTE.PA");
    }

    @Test
    @DisplayName("LVMH : MC.PA avant le hors-cote LVMHF, même si son symbole ressemble à la recherche")
    void horsCoteEnDernier() {
        assertThat(ranked("lvmh",
                new AssetSearchResult("LVMHF", "LVMH Moët Hennessy Louis Vuitton SE", "OTC Markets", AssetType.ACTION),
                new AssetSearchResult("MC.PA", "LVMH Moët Hennessy Louis Vuitton SE", "Paris", AssetType.ACTION)))
                .first().isEqualTo("MC.PA");
    }

    @Test
    @DisplayName("Bitcoin : la paire en euros d'abord")
    void cryptoEnEuros() {
        assertThat(ranked("bitcoin",
                new AssetSearchResult("BTC-USD", "Bitcoin USD", "CCC", AssetType.CRYPTO),
                new AssetSearchResult("BTC-EUR", "Bitcoin EUR", "CCC", AssetType.CRYPTO)))
                .first().isEqualTo("BTC-EUR");
    }

    @Test
    @DisplayName("Apple : la place d'origine (NASDAQ) avant une cotation secondaire allemande")
    void placeDOrigine() {
        assertThat(ranked("apple",
                new AssetSearchResult("APC.F", "Apple Inc.", "Frankfurt", AssetType.ACTION),
                new AssetSearchResult("AAPL", "Apple Inc.", "NASDAQ", AssetType.ACTION)))
                .first().isEqualTo("AAPL");
    }
}

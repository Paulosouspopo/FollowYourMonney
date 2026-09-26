package com.portfolio.tracker.analysis;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.marketdata.AssetProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Radiographie : classes, pays, secteurs, devises, expositions réelles, frais")
class ExposureCalculatorTest {

    private static final AssetProfile AIR_LIQUIDE = new AssetProfile("EQUITY", "L'Air Liquide S.A.", "France",
            "Basic Materials", Map.of(), List.of(), null, null, null, null, null);
    private static final AssetProfile WORLD_ETF = new AssetProfile("ETF", "Amundi MSCI World UCITS ETF", null, null,
            Map.of("technology", 0.30, "financial_services", 0.20, "healthcare", 0.50), List.of(
            new AssetProfile.Holding("AAPL", "Apple Inc", 0.05)), 0.99, 0.0, 0.01, 0.0, 0.38);
    private static final AssetProfile APPLE = new AssetProfile("EQUITY", "Apple Inc.", "United States", "Technology",
            Map.of(), List.of(), null, null, null, null, null);

    private static ExposureCalculator.Exposure sample() {
        return ExposureCalculator.compute(List.of(
                        new ExposureCalculator.Line("AI.PA", "Air Liquide", AssetType.ACTION, "EUR", 2000, null),
                        new ExposureCalculator.Line("CW8.PA", "Amundi MSCI World", AssetType.ETF, "EUR", 10000, null),
                        new ExposureCalculator.Line("AAPL", "Apple", AssetType.ACTION, "USD", 1000, null),
                        new ExposureCalculator.Line("BTC-EUR", "Bitcoin", AssetType.CRYPTO, "EUR", 500, null),
                        new ExposureCalculator.Line("~FCPE00000000", "FCPE maison", AssetType.FONDS, "EUR", 1000, 0.8)),
                List.of(new ExposureCalculator.Cash("FONDS_EUROS", "EUR", 1500)),
                Map.of("AI.PA", AIR_LIQUIDE, "CW8.PA", WORLD_ETF, "AAPL", APPLE), 42.5);
    }

    @Test
    @DisplayName("Classes d'actifs : la partie actions des fonds, le fonds euros, la crypto, un fonds non détaillé")
    void classes() {
        ExposureCalculator.Exposure e = sample();
        assertThat(e.totalEur()).isEqualTo(16000);
        assertThat(e.classes()).extracting(ExposureCalculator.Slice::key)
                .containsExactly("ACTIONS", "FONDS_EUROS", "FONDS_NON_DETAILLE", "CRYPTO", "LIQUIDITES");
        assertThat(e.classes().get(0).valueEur()).isEqualTo(12900); // 2000 + 9900 + 1000
        assertThat(e.equityEur()).isEqualTo(12900);
    }

    @Test
    @DisplayName("Pays : action = pays Yahoo, ETF = répartition estimée de son indice (MSCI World)")
    void countries() {
        ExposureCalculator.Exposure e = sample();
        Map<String, Double> byCountry = e.countries().stream()
                .collect(java.util.stream.Collectors.toMap(ExposureCalculator.Slice::key, ExposureCalculator.Slice::valueEur));
        assertThat(byCountry.get("US")).isCloseTo(9900 * 0.72 + 1000, within(0.01));
        assertThat(byCountry.get("FR")).isCloseTo(9900 * 0.027 + 2000, within(0.01));
        assertThat(e.countries().get(0).label()).isEqualTo("États-Unis");
        assertThat(e.countryKnownPct()).isEqualTo(100);
        assertThat(e.countryEstimatedPct()).isCloseTo(76.74, within(0.01)); // 9900 / 12900
    }

    @Test
    @DisplayName("Secteurs communs aux actions et aux fonds ; devises économiques ; expositions réelles regroupées")
    void sectorsCurrenciesAndRealExposure() {
        ExposureCalculator.Exposure e = sample();
        assertThat(e.sectors().get(0).key()).isEqualTo("healthcare"); // 4950
        assertThat(e.sectors()).extracting(ExposureCalculator.Slice::key).contains("technology", "basic_materials");
        assertThat(e.currencies().get(0).key()).isEqualTo("USD");
        // Air Liquide en direct (2000 €), puis Apple : 1000 € en direct + 5 % de l'ETF (500 €)
        assertThat(e.topExposures()).extracting(ExposureCalculator.RealExposure::symbol).startsWith("AI.PA", "AAPL");
        assertThat(e.topExposures().get(1)).satisfies(r -> {
            assertThat(r.symbol()).isEqualTo("AAPL");
            assertThat(r.valueEur()).isEqualTo(1500);
            assertThat(r.viaFunds()).isTrue();
        });
        assertThat(e.largestLineName()).isEqualTo("Amundi MSCI World");
        assertThat(e.largestLinePct()).isEqualTo(62.5);
    }

    @Test
    @DisplayName("Frais : TER Yahoo ou saisi, coût annuel, moyenne pondérée et coût sur 20 ans")
    void fees() {
        ExposureCalculator.Fees f = sample().fees();
        assertThat(f.brokerFeesPaidEur()).isEqualTo(42.5);
        assertThat(f.fundsValueEur()).isEqualTo(11000);
        assertThat(f.annualFundFeesEur()).isEqualTo(46); // 10000 × 0,38 % + 1000 × 0,8 %
        assertThat(f.weightedTerPct()).isEqualTo(0.418);
        assertThat(f.unknownValueEur()).isZero();
        assertThat(f.lines()).extracting(ExposureCalculator.FeeLine::userProvided).containsExactly(false, true);
        assertThat(f.twentyYearCostEur()).isBetween(1500.0, 2500.0);
    }

    @Test
    @DisplayName("Indices reconnus dans le nom d'un ETF")
    void indexNames() {
        assertThat(Geography.estimateFromName("BNP Paribas Easy S&P 500 UCITS ETF")).containsEntry("US", 1.0);
        assertThat(Geography.estimateFromName("iShares Core MSCI EM IMI UCITS ETF")).containsKey("CN");
        assertThat(Geography.estimateFromName("Vanguard FTSE All-World UCITS ETF")).containsKey("IN");
        assertThat(Geography.estimateFromName("Amundi PEA Nasdaq-100")).containsEntry("US", 1.0);
        assertThat(Geography.estimateFromName("Amundi Euro Stoxx 50")).containsKey("NL").doesNotContainKey("GB");
        assertThat(Geography.estimateFromName("Fonds Maison Patrimoine")).isEmpty();
    }
}

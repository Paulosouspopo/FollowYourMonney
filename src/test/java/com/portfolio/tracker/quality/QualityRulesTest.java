package com.portfolio.tracker.quality;

import com.portfolio.tracker.asset.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Règles de cohérence des saisies")
class QualityRulesTest {

    @Test
    @DisplayName("Écart au cours : toléré jusqu'à 15 % (25 % en crypto)")
    void ecart() {
        assertThat(QualityRules.deviation(new BigDecimal("25"), new BigDecimal("100"))).isEqualByComparingTo("-0.75");
        assertThat(QualityRules.isSuspicious(new BigDecimal("-0.75"), AssetType.ACTION)).isTrue();
        assertThat(QualityRules.isSuspicious(new BigDecimal("0.10"), AssetType.ACTION)).isFalse();
        assertThat(QualityRules.isSuspicious(new BigDecimal("0.20"), AssetType.ACTION)).isTrue();
        assertThat(QualityRules.isSuspicious(new BigDecimal("0.20"), AssetType.CRYPTO)).isFalse();
        assertThat(QualityRules.deviation(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("PEA : crypto et cotations hors UE signalées, places européennes acceptées")
    void pea() {
        assertThat(QualityRules.peaProblem("BTC-EUR", AssetType.CRYPTO)).isPresent();
        assertThat(QualityRules.peaProblem("AAPL", AssetType.ACTION)).isPresent();
        assertThat(QualityRules.peaProblem("VOD.L", AssetType.ACTION)).isPresent(); // Londres : hors UE
        assertThat(QualityRules.peaProblem("TTE.PA", AssetType.ACTION)).isEmpty();
        assertThat(QualityRules.peaProblem("SAP.DE", AssetType.ACTION)).isEmpty();
        assertThat(QualityRules.peaProblem("CW8.PA", AssetType.ETF)).isEmpty();
    }

    @Test
    @DisplayName("Division d'actions probable : écart d'au moins ×5 (ou ÷5), facteur arrondi")
    void division() {
        assertThat(QualityRules.splitFactor(new BigDecimal("1090"), new BigDecimal("5.45"))).hasValueSatisfying(
                f -> assertThat(f).isEqualByComparingTo("200"));
        assertThat(QualityRules.splitFactor(new BigDecimal("10"), new BigDecimal("100"))).hasValueSatisfying(
                f -> assertThat(f).isEqualByComparingTo("0.1"));
        assertThat(QualityRules.splitFactor(new BigDecimal("25"), new BigDecimal("100"))).isEmpty(); // ×4 : erreur de prix
    }
}

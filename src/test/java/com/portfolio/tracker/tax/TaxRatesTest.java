package com.portfolio.tracker.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaxRatesTest {

    @Test
    @DisplayName("Flat tax : 30 % jusqu'aux revenus 2025, 31,4 % à partir de 2026 (prélèvements sociaux 18,6 %)")
    void flatTaxParAnnee() {
        assertThat(TaxRates.flatTax(2025)).isEqualByComparingTo("0.30");
        assertThat(TaxRates.socialCharges(2025)).isEqualByComparingTo("0.172");
        assertThat(TaxRates.flatTax(2026)).isEqualByComparingTo("0.314");
        assertThat(TaxRates.socialCharges(2027)).isEqualByComparingTo("0.186");
    }

    @Test
    @DisplayName("Assurance-vie : prélèvements sociaux inchangés à 17,2 %")
    void assuranceVie() {
        assertThat(TaxRates.LIFE_INSURANCE_SOCIAL_CHARGES).isEqualByComparingTo("0.172");
    }
}

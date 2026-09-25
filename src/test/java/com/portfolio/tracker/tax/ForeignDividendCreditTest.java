package com.portfolio.tracker.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Crédit d'impôt sur dividendes étrangers (2AB)")
class ForeignDividendCreditTest {

    @Test
    @DisplayName("Pays : profil d'abord, sinon place de cotation ; taux conventionnel plafonné à 12,8 %")
    void countryAndRate() {
        assertThat(ForeignDividendCredit.country("AAPL", null)).isEqualTo("US");
        assertThat(ForeignDividendCredit.country("AI.PA", null)).isEqualTo("FR");
        assertThat(ForeignDividendCredit.country("SAP.DE", null)).isEqualTo("DE");
        assertThat(ForeignDividendCredit.country("RACE.MI", null)).isEqualTo("IT");
        assertThat(ForeignDividendCredit.country("ASML.AS", "NL")).isEqualTo("NL");
        assertThat(ForeignDividendCredit.country("~ABCDEFGHJKLM", null)).isNull();

        assertThat(ForeignDividendCredit.ratePct("US")).isEqualTo(12.8); // 15 % retenus, crédit plafonné
        assertThat(ForeignDividendCredit.ratePct("JP")).isEqualTo(10);
        assertThat(ForeignDividendCredit.ratePct("GB")).isZero();        // pas de retenue au Royaume-Uni
        assertThat(ForeignDividendCredit.ratePct("FR")).isZero();
        assertThat(ForeignDividendCredit.ratePct(null)).isZero();
    }
}

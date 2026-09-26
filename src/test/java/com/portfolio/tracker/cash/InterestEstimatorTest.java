package com.portfolio.tracker.cash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.portfolio.tracker.cash.InterestEstimator.Method.DAILY;
import static com.portfolio.tracker.cash.InterestEstimator.Method.QUINZAINE;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Intérêts estimés : quinzaines (livret) et prorata journalier (fonds euros)")
class InterestEstimatorTest {

    private static final BigDecimal THREE = new BigDecimal("3");

    private static InterestEstimator.Flow flow(int y, int m, int d, String amount) {
        return new InterestEstimator.Flow(LocalDate.of(y, m, d), new BigDecimal(amount));
    }

    @Test
    @DisplayName("Livret : un dépôt rapporte à partir de la quinzaine suivante, un retrait cesse dès la sienne")
    void quinzaines() {
        // Dépôt le 10 janvier : rapporte du 16 janvier au 31 décembre, soit 23 quinzaines × 1000 × 3 % / 24
        assertThat(InterestEstimator.estimate(List.of(flow(2025, 1, 10, "1000")), THREE, 2025,
                LocalDate.of(2025, 12, 31), QUINZAINE)).isEqualByComparingTo("28.75");
        // Retrait de 500 le 20 juillet : 11 quinzaines (16 juillet → 31 décembre) à 500 de moins
        assertThat(InterestEstimator.estimate(List.of(flow(2025, 1, 10, "1000"), flow(2025, 7, 20, "-500")), THREE, 2025,
                LocalDate.of(2025, 12, 31), QUINZAINE)).isEqualByComparingTo("21.88");
        // Année en cours : quinzaines commencées jusqu'à aujourd'hui (1er janvier → 15 mars = 5)
        assertThat(InterestEstimator.estimate(List.of(flow(2024, 5, 2, "2400")), THREE, 2025,
                LocalDate.of(2025, 3, 15), QUINZAINE)).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Fonds euros : chaque jour rapporte taux / 365 sur le solde, rien si le taux est absent")
    void daily() {
        assertThat(InterestEstimator.estimate(List.of(flow(2024, 6, 1, "1000")), new BigDecimal("2"), 2025,
                LocalDate.of(2025, 12, 31), DAILY)).isEqualByComparingTo("20.00");
        // 1000 € du 1er janvier au 30 juin puis 500 € (180,5 jours chacun ≈ moitié)
        BigDecimal half = InterestEstimator.estimate(List.of(flow(2024, 6, 1, "1000"), flow(2025, 7, 1, "-500")),
                new BigDecimal("2"), 2025, LocalDate.of(2025, 12, 31), DAILY);
        assertThat(half).isBetween(new BigDecimal("14.9"), new BigDecimal("15.0"));
        assertThat(InterestEstimator.estimate(List.of(flow(2024, 6, 1, "1000")), null, 2025,
                LocalDate.of(2025, 12, 31), DAILY)).isZero();
    }
}

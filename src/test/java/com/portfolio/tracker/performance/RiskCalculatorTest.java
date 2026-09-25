package com.portfolio.tracker.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Risque : volatilité, pire baisse, meilleur / pire jour")
class RiskCalculatorTest {

    @Test
    @DisplayName("Pire baisse d'un plus haut à un plus bas, week-ends ignorés, rien sous 20 jours ouvrés")
    void drawdownAndDays() {
        List<LocalDate> dates = new ArrayList<>();
        List<Double> cumulative = new ArrayList<>();
        LocalDate d = LocalDate.of(2025, 1, 1);
        double index = 1;
        for (int i = 0; i < 60; i++, d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek().getValue() >= 6;
            if (!weekend) {
                // +1 % par jour ouvré, sauf une chute de 10 % le 3 février puis reprise
                index *= d.equals(LocalDate.of(2025, 2, 3)) ? 0.90 : 1.01;
            }
            dates.add(d);
            cumulative.add(index - 1);
        }
        RiskCalculator.Risk r = RiskCalculator.compute(dates, cumulative);
        assertThat(r).isNotNull();
        assertThat(r.maxDrawdownPct()).isEqualTo(-10.0);
        assertThat(r.drawdownTrough()).isEqualTo(LocalDate.of(2025, 2, 3));
        assertThat(r.worstDayPct()).isEqualTo(-10.0);
        assertThat(r.bestDayPct()).isEqualTo(1.0);
        assertThat(r.volatilityPct()).isGreaterThan(0);
        assertThat(r.sharpe()).isNull(); // moins d'un an : pas de Sharpe

        assertThat(RiskCalculator.compute(dates.subList(0, 20), cumulative.subList(0, 20))).isNull();
    }
}

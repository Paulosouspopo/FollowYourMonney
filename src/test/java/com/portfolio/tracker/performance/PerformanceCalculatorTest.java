package com.portfolio.tracker.performance;

import com.portfolio.tracker.performance.PerformanceCalculator.Day;
import com.portfolio.tracker.performance.PerformanceCalculator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Performance — TWR et XIRR")
class PerformanceCalculatorTest {

    private static final LocalDate BASE = LocalDate.of(2025, 1, 1);

    @Test
    @DisplayName("Sans flux : TWR = variation de la valeur")
    void sansFlux() {
        Result r = PerformanceCalculator.compute(BASE, 1000, List.of(
                day(1, 1050, 0), day(2, 1100, 0)));
        assertThat(r.twr()).isCloseTo(0.10, within(1e-9));
        assertThat(r.gain()).isCloseTo(100, within(1e-9));
        assertThat(r.twrCumulative().get(0)).isCloseTo(0.05, within(1e-9));
    }

    @Test
    @DisplayName("Un versement ne crée pas de performance ; le TWR enchaîne les sous-périodes")
    void versementNeutre() {
        // +10 % le jour 1, versement de 1000 le jour 2 (valeur 2100 = 1100 + 1000, 0 %), puis -10 %
        Result r = PerformanceCalculator.compute(BASE, 1000, List.of(
                day(1, 1100, 0), day(2, 2100, 1000), day(3, 1890, 0)));
        assertThat(r.twr()).isCloseTo(1.10 * 0.90 - 1, within(1e-9)); // -1 %
        assertThat(r.netFlows()).isEqualTo(1000);
        assertThat(r.gain()).isCloseTo(-110, within(1e-9));
        // L'argent perd davantage que les placements : 1000 € arrivés juste avant la baisse
        assertThat(r.mwr()).isLessThan(r.twr());
    }

    @Test
    @DisplayName("Premier achat de la période : mesuré entre le prix payé et la clôture")
    void premierAchat() {
        Result r = PerformanceCalculator.compute(BASE, 0, List.of(
                day(1, 1020, 1000), day(2, 1071, 0)));
        assertThat(r.twrCumulative().get(0)).isCloseTo(0.02, within(1e-9));
        assertThat(r.twr()).isCloseTo(0.071, within(1e-9));
    }

    @Test
    @DisplayName("Tout vendu avec +10 %, jours sans capital, puis rachat : ni division par zéro ni rendement fictif")
    void sansCapital() {
        Result r = PerformanceCalculator.compute(BASE, 1000, List.of(
                day(1, 0, -1100), day(2, 0, 0), day(3, 510, 500)));
        assertThat(r.twr()).isCloseTo(1.1 * 1.02 - 1, within(1e-9));
    }

    @Test
    @DisplayName("XIRR : 1000 € devenus 1100 € en un an = 10 % par an")
    void xirrUnAn() {
        List<Day> days = new ArrayList<>();
        for (int i = 1; i <= 365; i++) {
            days.add(day(i, i == 365 ? 1100 : 1000, 0));
        }
        Result r = PerformanceCalculator.compute(BASE, 1000, days);
        assertThat(r.xirr()).isCloseTo(0.10, within(1e-6));
        assertThat(r.mwr()).isCloseTo(0.10, within(1e-6));
    }

    @Test
    @DisplayName("XIRR : versements réguliers (exemple de tableur, 3 flux)")
    void xirrTableur() {
        // -10000 le 01/01, -5000 au bout de 182 jours, valeur finale 16500 au bout d'un an
        List<double[]> flows = List.of(new double[] { 0, -10000 }, new double[] { 182 / 365.0, -5000 },
                new double[] { 1, 16500 });
        Double x = PerformanceCalculator.xirr(flows);
        assertThat(x).isNotNull();
        double npv = -10000 - 5000 / Math.pow(1 + x, 182 / 365.0) + 16500 / (1 + x);
        assertThat(npv).isCloseTo(0, within(1e-6));
        assertThat(x).isCloseTo(0.1197, within(1e-3));
    }

    @Test
    @DisplayName("Rien d'investi : pas de XIRR")
    void rienInvesti() {
        assertThat(PerformanceCalculator.compute(BASE, 0, List.of(day(1, 0, 0))).xirr()).isNull();
    }

    private static Day day(int offset, double value, double flow) {
        return new Day(BASE.plusDays(offset), value, flow);
    }
}

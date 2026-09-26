package com.portfolio.tracker.income;

import com.portfolio.tracker.marketdata.DividendEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Projection des dividendes")
class DividendProjectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    private static DividendEvent div(String date, String amount) {
        return new DividendEvent("TTE.PA", LocalDate.parse(date), new BigDecimal(amount), "EUR");
    }

    @Test
    @DisplayName("Trimestriel : 4 versements sur 12 mois, somme par action, prochains versements un an plus tard")
    void trimestriel() {
        List<DividendEvent> events = List.of(div("2025-06-30", "0.80"), div("2025-10-01", "0.85"),
                div("2025-12-31", "0.85"), div("2026-03-31", "0.85"), div("2026-06-30", "0.85"));

        List<DividendEvent> lastYear = DividendProjection.lastYear(events, TODAY);
        assertThat(lastYear).hasSize(4); // juin 2025 : plus d'un an
        assertThat(DividendProjection.perShare(lastYear)).isEqualByComparingTo("3.40");
        assertThat(DividendProjection.nextYear(lastYear, TODAY)).extracting(DividendEvent::exDate)
                .containsExactly(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"),
                        LocalDate.parse("2027-03-31"), LocalDate.parse("2027-06-30"));
    }

    @Test
    @DisplayName("Trimestriel au calendrier qui glisse : 3 détachements sur 365 jours restent un trimestriel")
    void frequenceParEcart() {
        List<DividendEvent> events = List.of(div("2024-09-25", "0.79"), div("2024-12-31", "0.79"),
                div("2025-03-31", "0.85"), div("2025-06-30", "0.85"), div("2025-09-24", "0.85"),
                div("2025-12-31", "0.85"), div("2026-03-31", "0.85"), div("2026-06-30", "0.85"));

        assertThat(DividendProjection.lastYear(events, TODAY)).hasSize(3); // 24/09/2025 : 366 jours
        assertThat(DividendProjection.paymentsPerYear(events, TODAY)).isEqualTo(4);
        List<DividendEvent> cycle = DividendProjection.lastCycle(events, TODAY);
        assertThat(cycle).hasSize(4);
        assertThat(DividendProjection.perShare(cycle)).isEqualByComparingTo("3.40");
    }

    @Test
    @DisplayName("Annuel et semestriel d'après l'écart entre deux détachements")
    void annuelEtSemestriel() {
        assertThat(DividendProjection.paymentsPerYear(List.of(div("2025-05-20", "2.00"), div("2026-05-19", "2.10")), TODAY))
                .isEqualTo(1);
        assertThat(DividendProjection.paymentsPerYear(List.of(div("2025-05-20", "1.00"), div("2025-11-20", "1.00"),
                div("2026-05-19", "1.10")), TODAY)).isEqualTo(2);
        assertThat(DividendProjection.paymentsPerYear(List.of(div("2026-05-19", "1.10")), TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("Rien versé depuis un an (capitalisant, suspendu) : aucune projection")
    void aucun() {
        List<DividendEvent> lastYear = DividendProjection.lastYear(List.of(div("2024-05-10", "1.00")), TODAY);
        assertThat(lastYear).isEmpty();
        assertThat(DividendProjection.perShare(lastYear)).isEqualByComparingTo("0");
    }
}

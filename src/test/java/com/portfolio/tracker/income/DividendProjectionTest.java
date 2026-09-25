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
    @DisplayName("Rien versé depuis un an (capitalisant, suspendu) : aucune projection")
    void aucun() {
        List<DividendEvent> lastYear = DividendProjection.lastYear(List.of(div("2024-05-10", "1.00")), TODAY);
        assertThat(lastYear).isEmpty();
        assertThat(DividendProjection.perShare(lastYear)).isEqualByComparingTo("0");
    }
}

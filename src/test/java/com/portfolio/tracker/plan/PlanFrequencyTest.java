package com.portfolio.tracker.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanFrequency — échéances et budget mensuel")
class PlanFrequencyTest {

    @Test
    @DisplayName("Mensuel démarré un 31 : dernier jour des mois courts, sans glisser ensuite")
    void mensuelFinDeMois() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertThat(PlanFrequency.MONTHLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(PlanFrequency.MONTHLY.occurrence(start, 2)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("Toutes les deux semaines, trimestriel, annuel")
    void autresFrequences() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        assertThat(PlanFrequency.BIWEEKLY.occurrence(start, 2)).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(PlanFrequency.QUARTERLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(PlanFrequency.YEARLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    @DisplayName("Équivalent mensuel")
    void budgetMensuel() {
        assertThat(PlanFrequency.WEEKLY.monthly(new BigDecimal("100"))).isEqualByComparingTo("433.33");
        assertThat(PlanFrequency.QUARTERLY.monthly(new BigDecimal("300"))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Date de fin : plus d'échéance au-delà")
    void dateDeFin() {
        InvestmentPlan plan = InvestmentPlan.builder()
                .frequency(PlanFrequency.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 2, 15))
                .build();
        plan.setNextExecutionDate(plan.computeNextDate());
        plan.advance();
        assertThat(plan.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        plan.advance();
        assertThat(plan.getNextExecutionDate()).isNull();
    }
}

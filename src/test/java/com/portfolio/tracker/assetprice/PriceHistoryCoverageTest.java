package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.PriceHistoryService.DateRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PriceHistoryService — plages à télécharger")
class PriceHistoryCoverageTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private static PriceHistoryCoverage coverage(LocalDate from, LocalDate to) {
        return PriceHistoryCoverage.builder().symbol("X").coveredFrom(from).coveredTo(to).build();
    }

    @Test
    @DisplayName("Symbole inconnu : tout l'intervalle jusqu'à aujourd'hui")
    void aucuneCouverture() {
        assertThat(PriceHistoryService.missingRanges(null, TODAY.minusDays(30), TODAY, YESTERDAY))
                .containsExactly(new DateRange(TODAY.minusDays(30), TODAY));
    }

    @Test
    @DisplayName("Déjà couvert jusqu'à hier : rien à faire")
    void dejaCouvert() {
        assertThat(PriceHistoryService.missingRanges(
                coverage(TODAY.minusDays(30), YESTERDAY), TODAY.minusDays(10), TODAY, YESTERDAY))
                .isEmpty();
    }

    @Test
    @DisplayName("Transaction plus ancienne : seulement le trou avant la couverture")
    void extensionVersLePasse() {
        assertThat(PriceHistoryService.missingRanges(
                coverage(TODAY.minusDays(30), YESTERDAY), TODAY.minusDays(40), TODAY, YESTERDAY))
                .containsExactly(new DateRange(TODAY.minusDays(40), TODAY.minusDays(31)));
    }

    @Test
    @DisplayName("Backend éteint trois semaines : seulement les jours manquants depuis la dernière couverture")
    void rattrapageApresArret() {
        assertThat(PriceHistoryService.missingRanges(
                coverage(TODAY.minusDays(60), TODAY.minusDays(22)), TODAY.minusDays(60), TODAY, YESTERDAY))
                .containsExactly(new DateRange(TODAY.minusDays(21), TODAY));
    }

    @Test
    @DisplayName("Couverture vide (achat saisi aujourd'hui) : on repart de son début, pas avant")
    void couvertureVide() {
        LocalDate lastWeek = TODAY.minusDays(7);
        assertThat(PriceHistoryService.missingRanges(
                coverage(lastWeek, lastWeek.minusDays(1)), lastWeek, TODAY, YESTERDAY))
                .containsExactly(new DateRange(lastWeek, TODAY));
    }
}

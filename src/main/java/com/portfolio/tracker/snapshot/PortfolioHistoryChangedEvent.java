package com.portfolio.tracker.snapshot;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Les données d'un portefeuille ont changé à partir de {@code from} : son
 * historique (snapshots) doit être recalculé depuis cette date.
 *
 * @param from null = recalcul complet depuis la première transaction
 */
public record PortfolioHistoryChangedEvent(UUID portfolioId, LocalDate from) {

    public static PortfolioHistoryChangedEvent full(UUID portfolioId) {
        return new PortfolioHistoryChangedEvent(portfolioId, null);
    }
}

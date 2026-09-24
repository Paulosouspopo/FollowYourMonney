package com.portfolio.tracker.marketdata;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction au-dessus de la source de données de marché.
 * Permet de changer de fournisseur (Yahoo aujourd'hui, un autre demain)
 * sans impacter AssetPriceService.
 */
public interface MarketDataProvider {
    Optional<MarketQuote> getQuote(String symbol);

    /**
     * Clôtures journalières sur [from, to] (bornes incluses).
     *
     * @return liste vide si le provider n'a aucune donnée sur la période
     * @throws MarketDataUnavailableException si le provider est injoignable
     */
    List<MarketPricePoint> getDailyHistory(String symbol, LocalDate from, LocalDate to);

    List<AssetSearchResult> search(String query);
}

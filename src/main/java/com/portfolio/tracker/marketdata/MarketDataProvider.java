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

    /**
     * Dividendes détachés sur [from, to], par action. Liste vide par défaut
     * (fournisseur sans dividendes) ; ne lève pas pour un actif qui n'en verse pas.
     *
     * @throws MarketDataUnavailableException si le provider est injoignable
     */
    default List<DividendEvent> getDividends(String symbol, LocalDate from, LocalDate to) {
        return List.of();
    }

    /**
     * Profil de l'actif (pays, secteur, répartition d'un fonds, frais). Vide
     * par défaut ou si le fournisseur ne le connaît pas ; ne lève jamais.
     */
    default java.util.Optional<AssetProfile> getProfile(String symbol) {
        return java.util.Optional.empty();
    }
}

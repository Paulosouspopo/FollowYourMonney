package com.portfolio.tracker.marketdata;

import java.util.Optional;

/**
 * Abstraction au-dessus de la source de données de marché.
 * Permet de changer de fournisseur (Yahoo aujourd'hui, un autre demain)
 * sans impacter AssetPriceService.
 */
public interface MarketDataProvider {

    /**
     * Récupère le dernier prix connu pour un symbole.
     *
     * @param symbol symbole tel qu'attendu par le provider (ex: "BTC-USD")
     * @return le résultat de cotation, vide si le symbole est introuvable ou l'API indisponible
     */
    Optional<MarketQuote> getQuote(String symbol);
}
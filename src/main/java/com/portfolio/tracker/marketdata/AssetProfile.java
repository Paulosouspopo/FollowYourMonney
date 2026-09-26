package com.portfolio.tracker.marketdata;

import java.util.List;
import java.util.Map;

/**
 * Profil de marché d'un actif (Yahoo quoteSummary). Tout est facultatif :
 * une action a un pays et un secteur ; un ETF ou un fonds, une répartition
 * sectorielle, parfois ses 10 premières lignes et ses frais.
 *
 * @param sectorWeights     clé de secteur Yahoo (technology, healthcare…) → part de 0 à 1
 * @param stockPct          part investie en actions (0 à 1), fonds seulement
 * @param expenseRatioPct   frais courants annuels en % (0.38 = 0,38 %)
 */
public record AssetProfile(String quoteType, String longName, String country, String sector,
                           Map<String, Double> sectorWeights, List<Holding> holdings,
                           Double stockPct, Double bondPct, Double cashPct, Double otherPct,
                           Double expenseRatioPct) {

    /** Ligne d'un fonds : symbole (souvent vide en Europe), nom, part de 0 à 1. */
    public record Holding(String symbol, String name, double weight) {
    }
}

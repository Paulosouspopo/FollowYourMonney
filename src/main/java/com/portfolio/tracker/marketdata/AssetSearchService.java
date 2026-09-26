package com.portfolio.tracker.marketdata;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.portfolio.tracker.asset.AssetType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetSearchService {

    private static final int MAX_RESULTS = 10;
    private static final int MIN_QUERY_LENGTH = 2;

    /** Places principales de la zone euro (cotation en euros) : favorisées pour un utilisateur français. */
    private static final Set<String> EURO_EXCHANGES = Set.of("Paris", "Amsterdam", "XETRA", "Milan", "Madrid",
            "Brussels", "Lisbon", "Irish", "Vienna", "Helsinki");
    private static final Set<String> EURO_SUFFIXES = Set.of(".PA", ".AS", ".DE", ".MI", ".MC", ".BR", ".LS", ".IR",
            ".VI", ".HE");
    /** Places principales américaines : cotation d'origine des actions US. */
    private static final Set<String> US_EXCHANGES = Set.of("NASDAQ", "NYSE", "NYSEArca", "NYSE American");

    private final MarketDataProvider marketDataProvider;

    public List<AssetSearchResult> search(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().length() < MIN_QUERY_LENGTH)
            return List.of();
        String query = rawQuery.trim().toLowerCase();

        return rank(marketDataProvider.search(query), query).stream()
                .limit(MAX_RESULTS)
                .toList();
    }

    /** Résultats Yahoo triés du plus pertinent au moins pertinent (aussi utilisé par l'import). */
    public static List<AssetSearchResult> rank(List<AssetSearchResult> results, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        return results.stream()
                .sorted(Comparator.comparingInt((AssetSearchResult r) -> relevance(r, query)).reversed())
                .toList();
    }

    /**
     * Score maison : plus il est haut, plus le résultat remonte.
     * "total" → "TotalEnergies SE" (startsWith) passe devant "Vanguard Total
     * Stock..." (contains).
     */
    static int relevance(AssetSearchResult r, String query) {
        String symbol = r.symbol().toLowerCase();
        String name = r.name() == null ? "" : r.name().toLowerCase();
        int score = 0;

        if (symbol.equals(query))
            score += 100;
        else if (symbol.startsWith(query))
            score += 60;

        if (name.equals(query))
            score += 90;
        else if (name.startsWith(query))
            score += 70;
        else if (name.matches(".*\\b" + Pattern.quote(query) + "\\b.*"))
            score += 30; // mot entier
        else if (name.contains(query))
            score += 10;

        if (r.assetType() == AssetType.ACTION)
            score += 15; // action avant ETF/crypto à égalité

        // Cotation : en euros d'abord (TTE.PA avant l'ADR TTE en dollars, BTC-EUR
        // avant BTC-USD), puis la place d'origine américaine ; le hors-cote
        // (LVMHF, TTFNF…) en dernier, il double souvent une vraie cotation.
        String exchange = r.exchange() == null ? "" : r.exchange();
        String upper = r.symbol().toUpperCase();
        if (EURO_EXCHANGES.contains(exchange) || EURO_SUFFIXES.stream().anyMatch(upper::endsWith))
            score += 25;
        else if (US_EXCHANGES.contains(exchange))
            score += 15;
        if (exchange.toUpperCase().contains("OTC") || exchange.equalsIgnoreCase("PNK"))
            score -= 80;
        if (r.assetType() == AssetType.CRYPTO && upper.endsWith("-EUR"))
            score += 30;

        return score;
    }
}
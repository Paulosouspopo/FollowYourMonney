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

    /** Places boursières favorisées pour un utilisateur français. */
    private static final Set<String> PREFERRED_EXCHANGES = Set.of("Paris", "NASDAQ", "NYSE", "NYSEArca", "CCC");

    private final MarketDataProvider marketDataProvider;

    public List<AssetSearchResult> search(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().length() < MIN_QUERY_LENGTH)
            return List.of();
        String query = rawQuery.trim().toLowerCase();

        return marketDataProvider.search(query).stream()
                .sorted(Comparator.comparingInt((AssetSearchResult r) -> relevance(r, query)).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    /**
     * Score maison : plus il est haut, plus le résultat remonte.
     * "total" → "TotalEnergies SE" (startsWith) passe devant "Vanguard Total
     * Stock..." (contains).
     */
    private int relevance(AssetSearchResult r, String query) {
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
        if (r.exchange() != null && PREFERRED_EXCHANGES.contains(r.exchange()))
            score += 10;

        return score;
    }
}
package com.portfolio.tracker.marketdata.yahoo;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.marketdata.AssetSearchResult;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.dto.YahooChartResponse;
import com.portfolio.tracker.marketdata.yahoo.dto.YahooSearchResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class YahooFinanceClient implements MarketDataProvider {

    private static final Set<String> ALLOWED_RANGES = Set.of("1mo", "3mo", "6mo", "1y", "2y", "5y", "10y", "max");

    private final RestClient restClient;

    public YahooFinanceClient(@Value("${app.yahoo.base-url}") String baseUrl,
            @Value("${app.yahoo.user-agent}") String userAgent) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    // ------------------------------------------------------------------ quote

    @Override
    public Optional<MarketQuote> getQuote(String symbol) {
        try {
            YahooChartResponse response = restClient.get()
                    .uri("/v8/finance/chart/{symbol}?range=1d&interval=1d", symbol)
                    .retrieve()
                    .body(YahooChartResponse.class);
            return firstResult(symbol, response).flatMap(this::toQuote);
        } catch (Exception e) {
            log.error("Yahoo quote failed for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- history

    @Override
    public List<MarketPricePoint> getDailyHistory(String symbol, String range) {
        if (!ALLOWED_RANGES.contains(range)) {
            throw new IllegalArgumentException("Range Yahoo invalide : " + range);
        }
        try {
            YahooChartResponse response = restClient.get()
                    .uri("/v8/finance/chart/{symbol}?range={range}&interval=1d", symbol, range)
                    .retrieve()
                    .body(YahooChartResponse.class);
            return firstResult(symbol, response).map(this::toPricePoints).orElse(List.of());
        } catch (Exception e) {
            log.error("Yahoo history failed for {} ({}): {}", symbol, range, e.getMessage());
            return List.of();
        }
    }

    // ----------------------------------------------------------------- search

    @Override
    public List<AssetSearchResult> search(String query) {
        try {
            YahooSearchResponse response = restClient.get()
                    .uri("/v1/finance/search?q={q}&quotesCount=20&newsCount=0&listsCount=0", query)
                    .retrieve()
                    .body(YahooSearchResponse.class);

            if (response == null || response.quotes() == null)
                return List.of();

            return response.quotes().stream()
                    .filter(q -> q.symbol() != null && Boolean.TRUE.equals(q.isYahooFinance()))
                    .map(q -> new AssetSearchResult(
                            q.symbol(),
                            firstNonBlank(q.longname(), q.shortname(), q.symbol()),
                            q.exchDisp(),
                            mapAssetType(q.quoteType())))
                    .filter(r -> r.assetType() != null) // on écarte INDEX, FUTURE, CURRENCY...
                    .toList();
        } catch (Exception e) {
            log.error("Yahoo search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------- parsing

    private Optional<YahooChartResponse.Result> firstResult(String symbol, YahooChartResponse response) {
        if (response == null || response.chart() == null
                || response.chart().result() == null || response.chart().result().isEmpty()) {
            log.warn("No result in Yahoo response for {} (error={})",
                    symbol, response != null && response.chart() != null ? response.chart().error() : "null");
            return Optional.empty();
        }
        return Optional.ofNullable(response.chart().result().get(0));
    }

    private Optional<MarketQuote> toQuote(YahooChartResponse.Result result) {
        YahooChartResponse.Meta meta = result.meta();
        if (meta == null || meta.regularMarketPrice() == null)
            return Optional.empty();

        return Optional.of(new MarketQuote(
                meta.symbol(),
                BigDecimal.valueOf(meta.regularMarketPrice()),
                meta.currency(),
                LocalDateTime.now(),
                firstNonBlank(meta.longName(), meta.shortName()),
                meta.fullExchangeName(),
                meta.instrumentType()));
    }

    private List<MarketPricePoint> toPricePoints(YahooChartResponse.Result result) {
        YahooChartResponse.Meta meta = result.meta();
        List<Long> timestamps = result.timestamp();
        if (meta == null || timestamps == null || result.indicators() == null
                || result.indicators().quote() == null || result.indicators().quote().isEmpty()) {
            return List.of();
        }
        List<Double> closes = result.indicators().quote().get(0).close();
        if (closes == null)
            return List.of();

        List<MarketPricePoint> points = new ArrayList<>(timestamps.size());
        for (int i = 0; i < Math.min(timestamps.size(), closes.size()); i++) {
            Double close = closes.get(i);
            if (close == null)
                continue; // jour férié / donnée manquante
            LocalDateTime asOf = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamps.get(i)), ZoneId.systemDefault());
            points.add(new MarketPricePoint(meta.symbol(), BigDecimal.valueOf(close), meta.currency(), asOf));
        }
        return points;
    }

    /**
     * Mapping Yahoo quoteType / instrumentType → notre enum. null = non supporté.
     */
    public static AssetType mapAssetType(String yahooType) {
        if (yahooType == null)
            return null;
        return switch (yahooType.toUpperCase()) {
            case "EQUITY" -> AssetType.ACTION;
            case "ETF" -> AssetType.ETF;
            case "CRYPTOCURRENCY" -> AssetType.CRYPTO;
            default -> null;
        };
    }

    private static String firstNonBlank(String... values) {
        for (String v : values)
            if (v != null && !v.isBlank())
                return v;
        return null;
    }

    @Override
    public List<MarketPricePoint> getDailyHistory(String symbol, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        try {
            YahooChartResponse response = restClient.get()
                    .uri("/v8/finance/chart/{symbol}?period1={p1}&period2={p2}&interval=1d",
                            symbol, period1, period2)
                    .retrieve()
                    .body(YahooChartResponse.class);
            return firstResult(symbol, response).map(this::toPricePoints).orElse(List.of());
        } catch (Exception e) {
            log.error("Yahoo bounded history failed for {} [{},{}]: {}", symbol, from, to, e.getMessage());
            return List.of();
        }
    }
}
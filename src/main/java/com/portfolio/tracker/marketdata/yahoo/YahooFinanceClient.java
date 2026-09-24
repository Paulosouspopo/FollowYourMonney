package com.portfolio.tracker.marketdata.yahoo;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.marketdata.AssetSearchResult;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketDataUnavailableException;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.dto.YahooChartResponse;
import com.portfolio.tracker.marketdata.yahoo.dto.YahooSearchResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class YahooFinanceClient implements MarketDataProvider {

    /**
     * Yahoo cote certaines places en sous-unités (pence, cents...). On ramène
     * tout à la devise ISO principale pour que les conversions EUR restent
     * justes (sinon un titre londonien vaut 100x trop).
     */
    private static final Map<String, String> MINOR_UNITS = Map.of(
            "GBp", "GBP",
            "GBX", "GBP",
            "ZAc", "ZAR",
            "ILA", "ILS");
    private static final BigDecimal MINOR_UNIT_DIVISOR = BigDecimal.valueOf(100);

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
    public List<MarketPricePoint> getDailyHistory(String symbol, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        YahooChartResponse response;
        try {
            response = restClient.get()
                    .uri("/v8/finance/chart/{symbol}?period1={p1}&period2={p2}&interval=1d",
                            symbol, period1, period2)
                    .retrieve()
                    .body(YahooChartResponse.class);
        } catch (Exception e) {
            throw new MarketDataUnavailableException(
                    "Yahoo history failed for " + symbol + " [" + from + "," + to + "]: " + e.getMessage(), e);
        }
        return firstResult(symbol, response)
                .map(this::toPricePoints)
                .orElse(List.of())
                .stream()
                // Yahoo renvoie parfois la barre du jour en cours hors période demandée
                .filter(p -> !p.date().isBefore(from) && !p.date().isAfter(to))
                .toList();
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

        ZoneId zone = exchangeZone(meta);
        LocalDate marketDate = meta.regularMarketTime() != null
                ? Instant.ofEpochSecond(meta.regularMarketTime()).atZone(zone).toLocalDate()
                : LocalDate.now(zone);

        return Optional.of(new MarketQuote(
                meta.symbol(),
                normalizePrice(BigDecimal.valueOf(meta.regularMarketPrice()), meta.currency()),
                normalizeCurrency(meta.currency()),
                LocalDateTime.now(),
                marketDate,
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

        ZoneId zone = exchangeZone(meta);
        String currency = normalizeCurrency(meta.currency());

        List<MarketPricePoint> points = new ArrayList<>(timestamps.size());
        for (int i = 0; i < Math.min(timestamps.size(), closes.size()); i++) {
            Double close = closes.get(i);
            if (close == null)
                continue; // jour férié / donnée manquante
            Instant instant = Instant.ofEpochSecond(timestamps.get(i));
            points.add(new MarketPricePoint(
                    meta.symbol(),
                    normalizePrice(BigDecimal.valueOf(close), meta.currency()),
                    currency,
                    instant.atZone(zone).toLocalDate(),
                    LocalDateTime.ofInstant(instant, ZoneId.systemDefault())));
        }
        return points;
    }

    /**
     * Fuseau de la place de cotation : c'est lui qui définit le "jour" d'une
     * clôture (une barre Tokyo horodatée 00:00 UTC appartient au jour J à Tokyo).
     */
    private static ZoneId exchangeZone(YahooChartResponse.Meta meta) {
        if (meta.exchangeTimezoneName() != null) {
            try {
                return ZoneId.of(meta.exchangeTimezoneName());
            } catch (DateTimeException e) {
                log.debug("Fuseau Yahoo inconnu : {}", meta.exchangeTimezoneName());
            }
        }
        return ZoneId.systemDefault();
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? null : MINOR_UNITS.getOrDefault(currency, currency);
    }

    private static BigDecimal normalizePrice(BigDecimal price, String rawCurrency) {
        return rawCurrency != null && MINOR_UNITS.containsKey(rawCurrency)
                ? price.divide(MINOR_UNIT_DIVISOR, 8, RoundingMode.HALF_UP)
                : price;
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
}

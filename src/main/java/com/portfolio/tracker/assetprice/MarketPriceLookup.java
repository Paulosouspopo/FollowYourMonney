package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.shared.MoneyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Cours d'un actif en EUR à une date donnée : clôture du jour (ou dernier
 * cours connu avant) pour une date passée, cotation courante pour aujourd'hui.
 * Sert aux prix estimés : conversions crypto importées, exécutions des
 * investissements programmés.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketPriceLookup {

    private final PriceHistoryService priceHistoryService;
    private final MarketDataProvider marketDataProvider;
    private final ExchangeRateService exchangeRateService;

    /** Plus bas et plus haut des clôtures, dans la devise de cotation. */
    public record ClosingRange(BigDecimal low, BigDecimal high, String currency, int days) {
    }

    /**
     * Clôtures de [from, before[ (le jour {@code before} exclu : sert à
     * comparer le cours du jour aux records précédents). Ne lève jamais.
     */
    public Optional<ClosingRange> closingRange(String symbol, LocalDate from, LocalDate before) {
        try {
            priceHistoryService.ensureCoverage(symbol, from);
            NavigableMap<LocalDate, DailyPrice> series = priceHistoryService
                    .loadSeries(List.of(symbol), from, before.minusDays(1))
                    .getOrDefault(symbol, new java.util.TreeMap<>())
                    .subMap(from, true, before, false);
            if (series.isEmpty()) {
                return Optional.empty();
            }
            BigDecimal low = null;
            BigDecimal high = null;
            for (DailyPrice p : series.values()) {
                low = low == null || p.price().compareTo(low) < 0 ? p.price() : low;
                high = high == null || p.price().compareTo(high) > 0 ? p.price() : high;
            }
            return Optional.of(new ClosingRange(low, high, series.firstEntry().getValue().currency(), series.size()));
        } catch (RuntimeException e) {
            log.warn("Historique de {} introuvable : {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /** Vide si le cours est introuvable (symbole inconnu, source injoignable). Ne lève jamais. */
    public Optional<BigDecimal> priceInEur(String symbol, LocalDate day) {
        if (symbol == null || symbol.isBlank() || day == null) {
            return Optional.empty();
        }
        try {
            Optional<DailyPrice> close;
            if (day.isBefore(LocalDate.now())) {
                priceHistoryService.ensureCoverage(symbol, day);
                close = priceHistoryService.findOnOrBefore(symbol, day);
            } else {
                close = marketDataProvider.getQuote(symbol)
                        .map(q -> new DailyPrice(q.marketDate(), q.price(), q.currency()));
            }
            return close
                    .map(p -> p.price().multiply(
                            exchangeRateService.getRateAsOf(p.currency(), MoneyConstants.BASE_CURRENCY, day)))
                    .filter(price -> price.signum() > 0);
        } catch (RuntimeException e) {
            log.warn("Cours de {} au {} introuvable : {}", symbol, day, e.getMessage());
            return Optional.empty();
        }
    }
}

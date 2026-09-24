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

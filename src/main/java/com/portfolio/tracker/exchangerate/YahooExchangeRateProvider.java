package com.portfolio.tracker.exchangerate;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class YahooExchangeRateProvider implements ExchangeRateProvider {

    private final MarketDataProvider marketDataProvider; // le YahooFinanceClient existant

    @Override
    public Optional<BigDecimal> getRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return Optional.of(BigDecimal.ONE);
        }
        String symbol = fromCurrency.toUpperCase() + toCurrency.toUpperCase() + "=X";
        return marketDataProvider.getQuote(symbol).map(MarketQuote::price);
    }
}

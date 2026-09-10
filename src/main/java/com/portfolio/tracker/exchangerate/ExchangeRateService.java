package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.exchangerate.dto.ExchangeRateResponse;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private static final int SCALE = 8;
    private static final List<String> TRACKED_CURRENCIES = List.of("EUR", "USD", "GBP", "CHF");

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateMapper exchangeRateMapper;
    private final ExchangeRateProvider exchangeRateProvider;

    @Transactional(readOnly = true)
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }

        return exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(fromCurrency, toCurrency)
                .map(ExchangeRate::getRate)
                .or(() -> exchangeRateRepository
                        .findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(toCurrency, fromCurrency)
                        .map(r -> BigDecimal.ONE.divide(r.getRate(), SCALE, RoundingMode.HALF_UP)))
                .orElseGet(() -> fetchAndPersistRate(fromCurrency, toCurrency));
    }

    /** Fallback synchrone si aucun taux n'est encore en base (ex: nouvelle devise). */
    private BigDecimal fetchAndPersistRate(String fromCurrency, String toCurrency) {
        BigDecimal rate = exchangeRateProvider.getRate(fromCurrency, toCurrency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Taux de change indisponible", fromCurrency + " -> " + toCurrency));
        saveRate(fromCurrency, toCurrency, rate, "yahoo-fallback");
        return rate;
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        return amount.multiply(getRate(fromCurrency, toCurrency)).setScale(2, RoundingMode.HALF_UP);
    }

    public ExchangeRateResponse findLatest(String fromCurrency, String toCurrency) {
        ExchangeRate exchangeRate = exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(fromCurrency, toCurrency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Taux de change", fromCurrency + " -> " + toCurrency));
        return exchangeRateMapper.toResponse(exchangeRate);
    }

    /** SCHEDULER : rafraîchit toutes les paires suivies, une fois par heure. */
    @Scheduled(cron = "${exchange.rate.update.cron:0 0 * * * *}")
    @Transactional
    public void updateAllTrackedRates() {
        log.info("========== Starting scheduled exchange rate update ==========");
        for (String from : TRACKED_CURRENCIES) {
            for (String to : TRACKED_CURRENCIES) {
                if (from.equals(to)) continue;
                exchangeRateProvider.getRate(from, to).ifPresentOrElse(
                        rate -> saveRate(from, to, rate, "yahoo"),
                        () -> log.warn("No rate returned for {} -> {}", from, to)
                );
            }
        }
        log.info("========== Finished scheduled exchange rate update ==========");
    }

    private void saveRate(String from, String to, BigDecimal rate, String source) {
        ExchangeRate exchangeRate = ExchangeRate.builder()
                .fromCurrency(from)
                .toCurrency(to)
                .rate(rate)
                .lastUpdated(LocalDateTime.now())
                .source(source)
                .build();
        exchangeRateRepository.save(exchangeRate);
    }
}

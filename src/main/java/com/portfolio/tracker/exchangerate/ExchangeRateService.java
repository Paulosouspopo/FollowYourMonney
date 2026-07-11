package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.exchangerate.dto.ExchangeRateIngestRequest;
import com.portfolio.tracker.exchangerate.dto.ExchangeRateResponse;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateService {

    private static final int SCALE = 8;

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateMapper exchangeRateMapper;

    public BigDecimal getLatestRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }

        return exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(fromCurrency, toCurrency)
                .map(ExchangeRate::getRate)
                .or(() -> exchangeRateRepository
                        .findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(toCurrency, fromCurrency)
                        .map(rate -> BigDecimal.ONE.divide(rate.getRate(), SCALE, RoundingMode.HALF_UP)))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Taux de change", fromCurrency + " -> " + toCurrency));
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        BigDecimal rate = getLatestRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public ExchangeRateResponse findLatest(String fromCurrency, String toCurrency) {
        ExchangeRate exchangeRate = exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(fromCurrency, toCurrency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Taux de change", fromCurrency + " -> " + toCurrency));
        return exchangeRateMapper.toResponse(exchangeRate);
    }

    @Transactional
    public ExchangeRateResponse ingest(ExchangeRateIngestRequest request) {
        ExchangeRate exchangeRate = exchangeRateMapper.toEntity(request);
        ExchangeRate saved = exchangeRateRepository.save(exchangeRate);
        return exchangeRateMapper.toResponse(saved);
    }
}
package com.portfolio.tracker.service;

import com.portfolio.tracker.entity.ExchangeRate;
import com.portfolio.tracker.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRate findByFromAndTo(String fromCurrency, String toCurrency) {
        return exchangeRateRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency)
                .orElseThrow(() -> new RuntimeException("Exchange rate not found: " + fromCurrency + " -> " + toCurrency));
    }

    public ExchangeRate save(ExchangeRate exchangeRate) {
        return exchangeRateRepository.save(exchangeRate);
    }

    public void deleteById(UUID id) {
        exchangeRateRepository.deleteById(id);
    }
}

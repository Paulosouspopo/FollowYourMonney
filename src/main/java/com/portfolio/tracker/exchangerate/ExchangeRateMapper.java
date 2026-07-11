package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.exchangerate.dto.ExchangeRateIngestRequest;
import com.portfolio.tracker.exchangerate.dto.ExchangeRateResponse;
import org.springframework.stereotype.Component;

@Component
public class ExchangeRateMapper {

    public ExchangeRate toEntity(ExchangeRateIngestRequest request) {
        return ExchangeRate.builder()
                .fromCurrency(request.fromCurrency())
                .toCurrency(request.toCurrency())
                .rate(request.rate())
                .lastUpdated(request.lastUpdated())
                .source(request.source())
                .build();
    }

    public ExchangeRateResponse toResponse(ExchangeRate exchangeRate) {
        return new ExchangeRateResponse(
                exchangeRate.getId(),
                exchangeRate.getFromCurrency(),
                exchangeRate.getToCurrency(),
                exchangeRate.getRate(),
                exchangeRate.getLastUpdated(),
                exchangeRate.getSource()
        );
    }
}
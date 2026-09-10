package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.exchangerate.dto.ExchangeRateResponse;
import org.springframework.stereotype.Component;

@Component
public class ExchangeRateMapper {

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

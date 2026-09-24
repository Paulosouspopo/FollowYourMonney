package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.exchangerate.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/latest")
    public ResponseEntity<ExchangeRateResponse> getLatest(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(exchangeRateService.findLatest(from, to));
    }

    /** Taux du jour 1 EUR = x devise, pour les devises d'affichage proposées. */
    @GetMapping("/display")
    public Map<String, BigDecimal> displayRates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        for (String currency : DisplayCurrency.SUPPORTED) {
            rates.put(currency, exchangeRateService.getRate("EUR", currency));
        }
        return rates;
    }

    @GetMapping("/convert")
    public ResponseEntity<BigDecimal> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(exchangeRateService.convert(amount, from, to));
    }
}
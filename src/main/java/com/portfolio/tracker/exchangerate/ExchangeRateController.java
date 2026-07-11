package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.exchangerate.dto.ExchangeRateIngestRequest;
import com.portfolio.tracker.exchangerate.dto.ExchangeRateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

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

    @GetMapping("/convert")
    public ResponseEntity<BigDecimal> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(exchangeRateService.convert(amount, from, to));
    }

    @PostMapping
    public ResponseEntity<ExchangeRateResponse> ingest(@Valid @RequestBody ExchangeRateIngestRequest request) {
        ExchangeRateResponse created = exchangeRateService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
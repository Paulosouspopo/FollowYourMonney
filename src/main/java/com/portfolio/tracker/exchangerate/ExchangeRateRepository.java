package com.portfolio.tracker.exchangerate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyOrderByLastUpdatedDesc(
            String fromCurrency,
            String toCurrency
    );

    List<ExchangeRate> findByFromCurrencyAndToCurrencyAndLastUpdatedBetweenOrderByLastUpdatedAsc(
            String fromCurrency,
            String toCurrency,
            LocalDateTime start,
            LocalDateTime end
    );
}
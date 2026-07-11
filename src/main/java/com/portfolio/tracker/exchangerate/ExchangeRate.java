package com.portfolio.tracker.exchangerate;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "exchange_rates",
        indexes = {
                @Index(name = "idx_exchange_rates_pair_last_updated",
                        columnList = "fromCurrency, toCurrency, lastUpdated")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fromCurrency;

    @Column(nullable = false)
    private String toCurrency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    private LocalDateTime lastUpdated;
    private String source;
}
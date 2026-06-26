package com.portfolio.tracker.entity;

import jakarta.persistence.*;
import lombok.*;

import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "asset_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    private String currency;
    private LocalDateTime lastUpdated;
    private String source;
}

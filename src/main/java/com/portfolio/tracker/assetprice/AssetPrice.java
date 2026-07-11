package com.portfolio.tracker.assetprice;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "asset_prices",
        indexes = {
                @Index(name = "idx_asset_prices_symbol_last_updated", columnList = "symbol, lastUpdated")
        }
)
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
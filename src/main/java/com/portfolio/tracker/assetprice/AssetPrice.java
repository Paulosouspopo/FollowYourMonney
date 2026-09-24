package com.portfolio.tracker.assetprice;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Prix journalier d'un symbole Yahoo : UNE ligne par (symbol, jour de bourse).
 *
 * Sert aussi bien aux actifs (BTC-EUR, TTE.PA...) qu'aux paires de devises
 * (USDEUR=X) : l'historique des taux de change est une série de marché comme
 * une autre.
 *
 * Le jour courant est mis à jour en place par le job horaire (upsert) ; les
 * jours passés portent la clôture officielle.
 */
@Entity
@Table(name = "asset_prices",
        indexes = @Index(name = "idx_asset_prices_symbol_price_date", columnList = "symbol, price_date"),
        uniqueConstraints = @UniqueConstraint(name = "uk_asset_prices_symbol_price_date",
                columnNames = { "symbol", "price_date" }))
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

        /** Jour de bourse auquel se rapporte le prix (fuseau de la place). */
        @Column(name = "price_date", nullable = false)
        private LocalDate priceDate;

        @Column(nullable = false, precision = 19, scale = 8)
        private BigDecimal price;

        private String currency;

        /** Moment où la valeur a été écrite / récupérée. */
        private LocalDateTime lastUpdated;
        private String source;

        @PrePersist
        @PreUpdate
        void defaultPriceDate() {
                if (priceDate == null && lastUpdated != null) {
                        priceDate = lastUpdated.toLocalDate();
                }
        }
}

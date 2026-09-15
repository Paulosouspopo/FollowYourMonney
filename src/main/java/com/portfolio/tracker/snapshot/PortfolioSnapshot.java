package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.portfolio.Portfolio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "portfolio_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_snapshot_portfolio_date",
                columnNames = {"portfolio_id", "snapshot_date"}
        ),
        indexes = @Index(
                name = "idx_snapshot_portfolio_date",
                columnList = "portfolio_id, snapshot_date"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    /** Jour du snapshot (une seule ligne par portfolio et par jour). */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalInvested;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal gainLoss;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal gainLossPercentage;

    @Column(nullable = false, length = 3)
    private String baseCurrency;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
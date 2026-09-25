package com.portfolio.tracker.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Profil de marché d'un symbole mis en cache (voir {@link AssetProfileService}). */
@Entity
@Table(name = "asset_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetProfileEntity {

    @Id
    private String symbol;

    @Column(name = "quote_type", length = 30)
    private String quoteType;

    @Column(name = "long_name")
    private String longName;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String sector;

    /** JSON : clé de secteur → part de 0 à 1. */
    @Column(name = "sector_weights", columnDefinition = "text")
    private String sectorWeights;

    /** JSON : 10 premières lignes d'un fonds. */
    @Column(columnDefinition = "text")
    private String holdings;

    @Column(name = "stock_pct", precision = 7, scale = 4)
    private BigDecimal stockPct;

    @Column(name = "bond_pct", precision = 7, scale = 4)
    private BigDecimal bondPct;

    @Column(name = "cash_pct", precision = 7, scale = 4)
    private BigDecimal cashPct;

    @Column(name = "other_pct", precision = 7, scale = 4)
    private BigDecimal otherPct;

    @Column(name = "expense_ratio_pct", precision = 6, scale = 3)
    private BigDecimal expenseRatioPct;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    /** false : Yahoo n'a rien renvoyé (nouvel essai au bout d'un jour). */
    @Column(nullable = false)
    private boolean ok;
}

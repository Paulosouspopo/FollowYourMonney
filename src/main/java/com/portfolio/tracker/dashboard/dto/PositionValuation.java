package com.portfolio.tracker.dashboard.dto;

import com.portfolio.tracker.asset.AssetType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Valorisation d'une position (un actif dans un portefeuille). Montants en EUR. */
@Getter
@Builder
public class PositionValuation {

    private UUID assetId;
    private String symbol;
    private String name;
    private AssetType assetType;

    /** Quantité détenue après toutes les opérations. */
    private BigDecimal quantity;

    /** Coût moyen unitaire pondéré (CUMP), en EUR. */
    private BigDecimal averageCostEur;

    /** Capital encore investi sur la position (CUMP × quantité), en EUR. */
    private BigDecimal investedEur;

    /** Valeur de marché actuelle, en EUR. */
    private BigDecimal currentValueEur;

    /** Plus/moins-value latente. */
    private BigDecimal unrealizedGainEur;
    private BigDecimal unrealizedGainPercentage;

    /** Plus/moins-value réalisée (ventes) + dividendes encaissés. */
    private BigDecimal realizedGainEur;
    private BigDecimal dividendsEur;

    /** Frais cumulés sur la position. */
    private BigDecimal totalFeesEur;

    /** Prix unitaire utilisé, en devise de cotation. */
    private BigDecimal lastPrice;
    private String priceCurrency;
    private LocalDateTime priceAsOf;

    /** true si aucun prix n'était disponible : la valorisation est incomplète. */
    private boolean priceMissing;
}
package com.portfolio.tracker.dashboard.dto;

import com.portfolio.tracker.portfolio.PortfolioType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Valorisation d'un portefeuille. Montants en EUR. */
@Getter
@Builder
public class PortfolioValuation {

    private UUID portfolioId;
    private String name;
    private PortfolioType type;

    private BigDecimal currentValueEur;
    private BigDecimal investedEur;
    private BigDecimal unrealizedGainEur;
    private BigDecimal unrealizedGainPercentage;
    private BigDecimal realizedGainEur;
    private BigDecimal dividendsEur;
    private BigDecimal totalFeesEur;

    private List<PositionValuation> positions;

    /** Nombre de positions encore ouvertes. */
    private int openPositionCount;

    /** true si au moins un prix manquait. */
    private boolean hasIncompletePrices;
}
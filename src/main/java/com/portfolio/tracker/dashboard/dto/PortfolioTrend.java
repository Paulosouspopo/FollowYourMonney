package com.portfolio.tracker.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Tendance récente d'un portefeuille (tuiles) : valeur jour par jour sur la
 * fenêtre, et variation de sa PLUS-VALUE sur cette fenêtre (un versement ou
 * un achat n'est pas un gain). En EUR.
 *
 * @param values    valeur de chaque jour, du plus ancien au plus récent
 * @param changePct variation rapportée à la valeur de départ (null si départ nul)
 */
public record PortfolioTrend(UUID portfolioId, List<BigDecimal> values, BigDecimal changeEur, BigDecimal changePct) {
}

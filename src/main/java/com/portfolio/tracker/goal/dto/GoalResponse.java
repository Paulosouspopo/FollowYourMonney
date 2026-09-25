package com.portfolio.tracker.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Objectif et où il en est. La projection (date d'atteinte, effort mensuel
 * nécessaire) est calculée par le front à partir de ces données.
 *
 * @param currentValueEur        valeur actuelle du périmètre
 * @param monthlyContributionEur investissements programmés actifs du périmètre, par mois
 * @param progressPct            valeur actuelle / cible, plafonnée à 100
 */
public record GoalResponse(UUID id, String name, BigDecimal targetAmount, LocalDate targetDate, UUID portfolioId,
                           String portfolioName, BigDecimal currentValueEur, BigDecimal monthlyContributionEur,
                           BigDecimal progressPct) {
}

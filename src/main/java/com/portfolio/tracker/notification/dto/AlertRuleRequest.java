package com.portfolio.tracker.notification.dto;

import com.portfolio.tracker.notification.alert.AlertRule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param portfolioId obligatoire pour le périmètre PORTFOLIO
 * @param symbol      symbole Yahoo choisi via la recherche (périmètre ASSET)
 * @param threshold   % pour une variation, EUR pour un seuil
 * @param period      obligatoire pour une variation (RISES, FALLS, MOVES)
 */
public record AlertRuleRequest(
        @NotNull(message = "Le périmètre est obligatoire")
        AlertRule.Scope scope,
        UUID portfolioId,
        @Size(max = 64)
        String symbol,
        @NotNull(message = "La condition est obligatoire")
        AlertRule.Condition condition,
        @NotNull(message = "Le seuil est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le seuil doit être strictement positif")
        BigDecimal threshold,
        AlertRule.Period period,
        boolean notifyEmail,
        Boolean enabled
) {}

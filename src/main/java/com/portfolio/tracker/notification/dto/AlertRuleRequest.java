package com.portfolio.tracker.notification.dto;

import com.portfolio.tracker.notification.alert.AlertRule;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param portfolioId obligatoire pour le périmètre PORTFOLIO
 * @param symbol      symbole Yahoo choisi via la recherche (périmètre ASSET)
 * @param threshold   % ou EUR selon la condition ; ignoré pour NEW_HIGH / NEW_LOW
 * @param period      variation (défaut 1 jour) ou record (défaut 1 an)
 * @param label       nom libre (facultatif)
 * @param notifyPush  null = oui
 * @param mutedUntil  sourdine jusqu'à cette date (null = active)
 */
public record AlertRuleRequest(
        @NotNull(message = "Le périmètre est obligatoire")
        AlertRule.Scope scope,
        UUID portfolioId,
        @Size(max = 64)
        String symbol,
        @NotNull(message = "La condition est obligatoire")
        AlertRule.Condition condition,
        BigDecimal threshold,
        AlertRule.Period period,
        boolean notifyEmail,
        Boolean enabled,
        @Size(max = 100)
        String label,
        Boolean notifyPush,
        LocalDateTime mutedUntil
) {
    /** Sans nom, push activé, pas de sourdine. */
    public AlertRuleRequest(AlertRule.Scope scope, UUID portfolioId, String symbol, AlertRule.Condition condition,
            BigDecimal threshold, AlertRule.Period period, boolean notifyEmail, Boolean enabled) {
        this(scope, portfolioId, symbol, condition, threshold, period, notifyEmail, enabled, null, null, null);
    }
}

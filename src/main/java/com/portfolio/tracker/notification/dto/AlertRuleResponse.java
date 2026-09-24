package com.portfolio.tracker.notification.dto;

import com.portfolio.tracker.notification.alert.AlertRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** @param description règle en français (« BTC-EUR baisse de 5 % sur 1 jour ») */
public record AlertRuleResponse(
        UUID id,
        AlertRule.Scope scope,
        UUID portfolioId,
        String portfolioName,
        String symbol,
        String assetName,
        AlertRule.Condition condition,
        BigDecimal threshold,
        AlertRule.Period period,
        boolean notifyEmail,
        boolean enabled,
        LocalDateTime lastTriggeredAt,
        String description
) {}

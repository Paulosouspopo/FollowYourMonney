package com.portfolio.tracker.plan.dto;

import com.portfolio.tracker.plan.InvestmentPlan;
import com.portfolio.tracker.plan.PlanFrequency;
import com.portfolio.tracker.portfolio.PortfolioType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param nextExecutionDate null : plan terminé
 * @param monthlyAmount     équivalent mensuel du montant (budget)
 * @param lastError         dernière échéance sautée ou erreur en attente de nouvel essai
 */
public record PlanResponse(
        UUID id,
        UUID portfolioId,
        String portfolioName,
        PortfolioType portfolioType,
        InvestmentPlan.Type type,
        String symbol,
        String name,
        BigDecimal amount,
        BigDecimal fees,
        PlanFrequency frequency,
        LocalDate startDate,
        LocalDate endDate,
        boolean fractional,
        boolean active,
        int occurrences,
        LocalDate nextExecutionDate,
        LocalDate lastExecutionDate,
        String lastError,
        BigDecimal monthlyAmount
) {}

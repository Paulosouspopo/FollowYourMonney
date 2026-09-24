package com.portfolio.tracker.plan.dto;

import com.portfolio.tracker.plan.InvestmentPlan;
import com.portfolio.tracker.plan.PlanFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Création ou modification d'un plan (PUT complet). Après la première
 * échéance, le type, l'actif, la fréquence et la date de début sont figés.
 *
 * @param symbol     symbole Yahoo choisi via la recherche (BUY uniquement)
 * @param amount     montant de chaque échéance en EUR, frais compris
 * @param fractional false : parts entières uniquement
 * @param active     null = actif
 */
public record PlanRequest(
        @NotNull(message = "Le type est obligatoire")
        InvestmentPlan.Type type,

        @Size(max = 64)
        String symbol,

        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le montant doit être strictement positif")
        BigDecimal amount,

        @DecimalMin(value = "0.0", message = "Les frais doivent être positifs ou nuls")
        BigDecimal fees,

        @NotNull(message = "La fréquence est obligatoire")
        PlanFrequency frequency,

        @NotNull(message = "La date de début est obligatoire")
        LocalDate startDate,

        LocalDate endDate,

        Boolean fractional,

        Boolean active
) {}

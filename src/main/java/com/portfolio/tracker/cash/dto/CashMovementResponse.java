package com.portfolio.tracker.cash.dto;

import com.portfolio.tracker.cash.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Montant toujours positif (le type donne le sens), dans sa devise ; `amountEur` au taux du jour du mouvement. */
public record CashMovementResponse(
        UUID id,
        UUID portfolioId,
        CashMovementType type,
        BigDecimal amount,
        String currency,
        BigDecimal amountEur,
        BigDecimal counterAmount,
        String counterCurrency,
        LocalDate movementDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

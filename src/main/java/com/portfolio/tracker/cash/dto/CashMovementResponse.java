package com.portfolio.tracker.cash.dto;

import com.portfolio.tracker.cash.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Montant en EUR, toujours positif (le type donne le sens). */
public record CashMovementResponse(
        UUID id,
        UUID portfolioId,
        CashMovementType type,
        BigDecimal amount,
        LocalDate movementDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

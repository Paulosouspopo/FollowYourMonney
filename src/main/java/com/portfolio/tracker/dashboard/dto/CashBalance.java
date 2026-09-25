package com.portfolio.tracker.dashboard.dto;

import java.math.BigDecimal;

/** Solde de liquidités dans une devise, et sa valeur en euros au taux du jour. */
public record CashBalance(String currency, BigDecimal amount, BigDecimal amountEur) {
}

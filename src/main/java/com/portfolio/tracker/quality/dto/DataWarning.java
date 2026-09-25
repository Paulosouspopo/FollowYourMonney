package com.portfolio.tracker.quality.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Avertissement pendant une saisie (rien n'est bloqué).
 *
 * @param code           PRICE_MISMATCH ou PEA_INELIGIBLE
 * @param suggestedPrice cours de clôture du jour, dans la devise saisie (PRICE_MISMATCH)
 * @param marketDate     jour de ce cours
 */
public record DataWarning(String code, String message, BigDecimal suggestedPrice, String currency, LocalDate marketDate) {
}

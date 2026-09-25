package com.portfolio.tracker.quality.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Incohérence dans les données déjà saisies (bandeau « à vérifier »).
 *
 * @param key            identifiant stable, pour l'ignorer (« PRICE:&lt;transaction&gt; », « PEA:&lt;actif&gt; »)
 * @param transactionId  opération concernée (null pour un actif mal placé)
 * @param suggestedPrice cours de clôture du jour, dans la devise de l'opération
 */
public record DataIssue(String key, String code, UUID portfolioId, String portfolioName, UUID transactionId,
                        String symbol, LocalDate date, String message, BigDecimal suggestedPrice, String currency) {
}

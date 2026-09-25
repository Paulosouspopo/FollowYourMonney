package com.portfolio.tracker.income.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Revenus passifs : ce qui a été reçu, ce qui est attendu. Montants en EUR,
 * pourcentages en « 3.45 » (= 3,45 %).
 *
 * @param annualProjectedEur dividendes des 12 derniers mois × quantités détenues + intérêts des livrets
 * @param yieldOnCostPct     revenu annuel projeté / prix de revient des positions qui versent
 * @param received           reçu par mois (24 derniers mois, du plus ancien au plus récent)
 * @param upcoming           prochains versements estimés (12 mois), par date
 */
public record IncomeResponse(
        BigDecimal annualProjectedEur,
        BigDecimal monthlyProjectedEur,
        BigDecimal receivedLast12mEur,
        BigDecimal receivedThisYearEur,
        BigDecimal yieldOnCostPct,
        List<PositionIncome> positions,
        List<MonthIncome> received,
        List<UpcomingPayment> upcoming
) {
    /**
     * @param perShare       dividendes des 12 derniers mois par action, devise de cotation
     * @param paymentsPerYear versements sur les 12 derniers mois (4 = trimestriel)
     */
    public record PositionIncome(UUID portfolioId, String portfolioName, String symbol, String name,
                                 BigDecimal quantity, BigDecimal perShare, String currency, BigDecimal annualEur,
                                 BigDecimal yieldOnCostPct, BigDecimal currentYieldPct, int paymentsPerYear,
                                 LocalDate lastExDate, String kind) {
    }

    /** @param month « 2026-03 » */
    public record MonthIncome(String month, BigDecimal dividendsEur, BigDecimal interestEur) {
    }

    /** @param estimated true : date et montant déduits de l'an dernier */
    public record UpcomingPayment(LocalDate date, String symbol, String name, BigDecimal amountEur, String kind,
                                  boolean estimated) {
    }
}

package com.portfolio.tracker.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Récapitulatif fiscal d'une année (revenus de l'année {@code year}, déclarés
 * l'année suivante). Montants en EUR. Estimation d'après les opérations
 * saisies ou importées : pas un conseil fiscal.
 *
 * @param years années disponibles (avec des cessions ou des revenus)
 */
public record TaxReport(int year, List<Integer> years, Securities securities, Crypto crypto, List<PeaStatus> peas,
                        int marginalTaxRate, RetirementSavings retirementSavings, List<LifeInsuranceStatus> lifeInsurances,
                        List<EmployeeSavingsStatus> employeeSavings, List<String> reminders) {

    /**
     * Versements de l'année sur les PER (déductibles du revenu imposable, dans
     * la limite du plafond indiqué sur l'avis d'impôt).
     *
     * @param estimatedSavingEur versements × tranche marginale
     */
    public record RetirementSavings(BigDecimal depositsEur, BigDecimal estimatedSavingEur, List<Box> boxes) {
    }

    /**
     * Contrat d'assurance-vie : ancienneté (8 ans : abattement annuel de
     * 4 600 € / 9 200 € sur les gains retirés) et rachats de l'année.
     *
     * @param withdrawalsGainEur part de gains estimée dans les rachats de l'année (au prorata gain / valeur actuels)
     */
    public record LifeInsuranceStatus(UUID portfolioId, String name, LocalDate openedAt, boolean openedAtEstimated,
                                      LocalDate eightYearsDate, boolean eightYearsReached, BigDecimal depositsEur,
                                      BigDecimal valueEur, BigDecimal gainEur, BigDecimal withdrawalsEur,
                                      BigDecimal withdrawalsGainEur) {
    }

    /**
     * Épargne salariale : gains exonérés d'impôt sur le revenu, prélèvements
     * sociaux (17,2 %) au déblocage.
     */
    public record EmployeeSavingsStatus(UUID portfolioId, String name, BigDecimal depositsEur,
                                        BigDecimal employerContributionsEur, BigDecimal valueEur, BigDecimal gainEur,
                                        BigDecimal socialChargesIfWithdrawnEur) {
    }

    /** Case de déclaration (ex. 3VG, formulaire 2042). */
    public record Box(String code, String label, BigDecimal amountEur, String form) {
    }

    /**
     * Valeurs mobilières hors PEA.
     *
     * @param carriedLossesUsedEur moins-values des années précédentes imputées cette année
     * @param lossesCarryForwardEur moins-values encore reportables sur les années suivantes
     * @param estimatedTaxEur       flat tax 30 % (12,8 % d'impôt + 17,2 % de prélèvements sociaux)
     */
    public record Securities(List<Sale> sales, BigDecimal gainsEur, BigDecimal lossesEur, BigDecimal netEur,
                             BigDecimal carriedLossesUsedEur, BigDecimal taxableGainEur, BigDecimal lossesCarryForwardEur,
                             BigDecimal dividendsEur, BigDecimal estimatedTaxEur, List<Box> boxes) {
    }

    public record Sale(LocalDate date, String symbol, String name, BigDecimal quantity, BigDecimal proceedsEur,
                       BigDecimal costEur, BigDecimal gainEur) {
    }

    /**
     * Crypto-actifs (150 VH bis, formulaire 2086).
     *
     * @param exempt cessions de l'année ≤ 305 € : exonérées
     */
    public record Crypto(List<CryptoSale> sales, BigDecimal totalProceedsEur, BigDecimal netGainEur, boolean exempt,
                         BigDecimal estimatedTaxEur, List<Box> boxes) {
    }

    public record CryptoSale(LocalDate date, String symbol, BigDecimal proceedsEur, BigDecimal portfolioValueEur,
                             BigDecimal acquisitionShareEur, BigDecimal gainEur) {
    }

    /**
     * @param openedAtEstimated date d'ouverture non renseignée : première opération
     * @param depositsEstimated sans suivi des liquidités : achats - ventes
     * @param socialChargesIfWithdrawnEur prélèvements sociaux (17,2 %) sur le gain en cas de retrait total aujourd'hui
     */
    public record PeaStatus(UUID portfolioId, String name, LocalDate openedAt, boolean openedAtEstimated,
                            LocalDate fiveYearsDate, boolean fiveYearsReached, BigDecimal depositsEur,
                            boolean depositsEstimated, BigDecimal ceilingEur, BigDecimal valueEur,
                            BigDecimal socialChargesIfWithdrawnEur) {
    }
}

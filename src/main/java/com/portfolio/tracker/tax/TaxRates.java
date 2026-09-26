package com.portfolio.tracker.tax;

import java.math.BigDecimal;

/**
 * Taux de la flat tax (PFU) selon l'année des revenus.
 *
 * <ul>
 * <li>Impôt sur le revenu : 12,8 %.</li>
 * <li>Prélèvements sociaux : 17,2 % jusqu'en 2025, 18,6 % à partir des revenus
 * 2026 (contribution financière pour l'autonomie de 1,4 point, LFSS 2026) sur
 * les dividendes, plus-values mobilières, crypto, gains du PEA, épargne
 * salariale et PER.</li>
 * <li>Assurance-vie (et PEL, CEL, PEP) : 17,2 % inchangé.</li>
 * </ul>
 */
public final class TaxRates {

    static final BigDecimal INCOME_TAX = new BigDecimal("0.128");
    static final BigDecimal SOCIAL_CHARGES_BEFORE_2026 = new BigDecimal("0.172");
    static final BigDecimal SOCIAL_CHARGES_FROM_2026 = new BigDecimal("0.186");
    /** Assurance-vie : exclue de la hausse de 2026. */
    static final BigDecimal LIFE_INSURANCE_SOCIAL_CHARGES = SOCIAL_CHARGES_BEFORE_2026;

    private TaxRates() {
    }

    /** Prélèvements sociaux sur les revenus du capital de l'année (hors assurance-vie). */
    public static BigDecimal socialCharges(int year) {
        return year >= 2026 ? SOCIAL_CHARGES_FROM_2026 : SOCIAL_CHARGES_BEFORE_2026;
    }

    /** Flat tax complète : 12,8 % + prélèvements sociaux de l'année. */
    public static BigDecimal flatTax(int year) {
        return INCOME_TAX.add(socialCharges(year));
    }
}

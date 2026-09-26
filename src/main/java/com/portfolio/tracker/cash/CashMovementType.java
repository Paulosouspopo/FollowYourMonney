package com.portfolio.tracker.cash;

import java.math.BigDecimal;

/** Nature d'un mouvement d'argent et son effet sur le solde. */
public enum CashMovementType {
    /** Versement (apport d'argent frais). */
    DEPOSIT(1),
    /** Retrait vers l'extérieur. */
    WITHDRAWAL(-1),
    /** Intérêts crédités (livret, rémunération des espèces). */
    INTEREST(1),
    /** Frais de tenue de compte, droits de garde... (hors frais de courtage). */
    FEE(-1),
    /** Abondement de l'employeur (épargne salariale, PER collectif) : un apport qui ne sort pas de ta poche. */
    ABONDEMENT(1),
    /**
     * Change entre deux devises du compte : le montant quitte la devise du
     * mouvement, la contrepartie arrive dans l'autre. Ni apport ni retrait.
     */
    CONVERSION(-1);

    private final int sign;

    CashMovementType(int sign) {
        this.sign = sign;
    }

    /** Montant signé : positif s'il augmente le solde. */
    public BigDecimal signed(BigDecimal amount) {
        return sign > 0 ? amount : amount.negate();
    }
}

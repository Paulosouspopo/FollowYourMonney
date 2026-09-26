package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.dashboard.CashState;
import com.portfolio.tracker.transaction.Transaction;

import java.math.BigDecimal;

/**
 * Flux externes d'un portefeuille, pour la performance (TWR, XIRR) : ce qui
 * entre ou sort de l'argent de l'investisseur, par opposition à ce que le
 * portefeuille gagne ou perd.
 *
 * <ul>
 * <li>Compte avec suivi des liquidités : versements - retraits. Intérêts,
 * dividendes et frais restent dans le compte : c'est du rendement. Un solde
 * négatif (achat sans le versement correspondant) est traité comme un
 * versement implicite : sans cela, un achat non financé ressemblerait à une
 * perte de 100 %.</li>
 * <li>Compte sans suivi : chaque achat est un apport (montant + frais), chaque
 * vente et chaque dividende net un retrait (l'argent quitte le périmètre suivi).</li>
 * </ul>
 */
public final class PerformanceFlows {

    private PerformanceFlows() {
    }

    /** Compte sans suivi des liquidités : flux d'une opération. */
    public static BigDecimal tradeFlow(Transaction tx) {
        BigDecimal amount = nz(tx.getTotalAmountEur());
        BigDecimal fees = nz(tx.getFeesEur());
        return switch (tx.getType()) {
            case BUY -> amount.add(fees);
            case SELL, DIVIDEND -> amount.subtract(fees).negate();
        };
    }

    /**
     * Découvert : partie négative du solde (valorisé en euros), comptée comme
     * apport implicite.
     */
    public static BigDecimal deficit(BigDecimal cashValueEur) {
        return cashValueEur.signum() < 0 ? cashValueEur.negate() : BigDecimal.ZERO;
    }

    /** Apports cumulés d'un compte suivi : versements nets + découvert. */
    public static BigDecimal contributed(CashState cash, BigDecimal cashValueEur) {
        return cash.getNetDepositsEur().add(deficit(cashValueEur));
    }

    /**
     * Valeur d'un compte suivi : positions + solde positif. Un découvert
     * correspond à des versements non saisis (déjà comptés dans
     * {@link #contributed}) : il ne diminue pas la valeur.
     */
    public static BigDecimal trackedValue(BigDecimal positionsValueEur, BigDecimal cashValueEur) {
        return positionsValueEur.add(cashValueEur.max(BigDecimal.ZERO));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

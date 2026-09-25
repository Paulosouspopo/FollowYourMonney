package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.transaction.Transaction;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Liquidités d'un portefeuille, construites en rejouant ses mouvements
 * d'argent et le flux de trésorerie de ses opérations. Comme
 * {@link PositionState}, source unique partagée par la valorisation live et
 * la reconstruction de l'historique : le dashboard et la courbe donnent le
 * même solde.
 *
 * Flux des opérations (en EUR au taux du jour de l'opération) :
 * <ul>
 * <li>achat : - (montant + frais) ;</li>
 * <li>vente : + (montant - frais) ;</li>
 * <li>dividende : + (montant - frais).</li>
 * </ul>
 * Le solde peut être négatif (achats saisis sans le versement correspondant) :
 * il est affiché tel quel pour inviter à compléter les versements.
 *
 * Classe mutable, non thread-safe : une instance par portefeuille et par calcul.
 */
@Getter
public class CashState {

    private BigDecimal balanceEur = BigDecimal.ZERO;
    /** Versements - retraits : l'argent réellement apporté de l'extérieur. */
    private BigDecimal netDepositsEur = BigDecimal.ZERO;
    private BigDecimal interestEur = BigDecimal.ZERO;
    /** Frais de tenue de compte / droits de garde (hors frais de courtage). */
    private BigDecimal accountFeesEur = BigDecimal.ZERO;

    public void apply(CashMovement movement) {
        BigDecimal amount = movement.getAmount();
        balanceEur = balanceEur.add(movement.signedAmount());
        switch (movement.getType()) {
            case DEPOSIT -> netDepositsEur = netDepositsEur.add(amount);
            case WITHDRAWAL -> netDepositsEur = netDepositsEur.subtract(amount);
            case INTEREST -> interestEur = interestEur.add(amount);
            case FEE -> accountFeesEur = accountFeesEur.add(amount);
        }
    }

    public void apply(Transaction tx) {
        BigDecimal amount = nz(tx.getTotalAmountEur());
        BigDecimal fees = nz(tx.getFeesEur());
        balanceEur = switch (tx.getType()) {
            case BUY -> balanceEur.subtract(amount).subtract(fees);
            case SELL, DIVIDEND -> balanceEur.add(amount).subtract(fees);
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

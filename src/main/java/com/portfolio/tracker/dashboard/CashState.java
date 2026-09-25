package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.Transaction;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Liquidités d'un portefeuille, construites en rejouant ses mouvements
 * d'argent et le flux de trésorerie de ses opérations. Comme
 * {@link PositionState}, source unique partagée par la valorisation live et
 * la reconstruction de l'historique : le dashboard et la courbe donnent le
 * même solde.
 *
 * Flux des opérations :
 * <ul>
 * <li>achat : - (montant + frais) ;</li>
 * <li>vente : + (montant - frais) ;</li>
 * <li>dividende : + (montant - frais).</li>
 * </ul>
 * Compte en euros : flux convertis au taux du jour de l'opération. Compte
 * multidevise : une opération en devise est réglée dans cette devise (solde
 * USD, GBP…), valorisée ensuite au taux du jour de la valorisation
 * ({@link #valueEur}). Les apports, intérêts et frais sont retenus en euros au
 * taux du jour du mouvement (ce qui est sorti de la poche ne bouge plus).
 *
 * Le solde peut être négatif (achats saisis sans le versement correspondant) :
 * il est affiché tel quel pour inviter à compléter les versements.
 *
 * Classe mutable, non thread-safe : une instance par portefeuille et par calcul.
 */
@Getter
public class CashState {

    private final boolean multiCurrency;
    /** Solde en euros. */
    private BigDecimal balanceEur = BigDecimal.ZERO;
    /** Soldes dans les autres devises (compte multidevise), par code ISO. */
    private final Map<String, BigDecimal> foreignBalances = new TreeMap<>();
    /** Versements - retraits (+ abondement) : l'argent réellement apporté de l'extérieur, en EUR. */
    private BigDecimal netDepositsEur = BigDecimal.ZERO;
    /** Dont abondement de l'employeur. */
    private BigDecimal employerContributionsEur = BigDecimal.ZERO;
    private BigDecimal interestEur = BigDecimal.ZERO;
    /** Frais de tenue de compte / droits de garde (hors frais de courtage). */
    private BigDecimal accountFeesEur = BigDecimal.ZERO;

    public CashState() {
        this(false);
    }

    public CashState(boolean multiCurrency) {
        this.multiCurrency = multiCurrency;
    }

    public void apply(CashMovement movement) {
        String currency = iso(movement.getCurrency());
        if (movement.getType() == com.portfolio.tracker.cash.CashMovementType.CONVERSION) {
            add(currency, movement.getAmount().negate());
            add(iso(movement.getCounterCurrency()), movement.getCounterAmount());
            return;
        }
        add(currency, movement.signedAmount());
        BigDecimal amountEur = movement.amountEur();
        switch (movement.getType()) {
            case DEPOSIT -> netDepositsEur = netDepositsEur.add(amountEur);
            case ABONDEMENT -> {
                netDepositsEur = netDepositsEur.add(amountEur);
                employerContributionsEur = employerContributionsEur.add(amountEur);
            }
            case WITHDRAWAL -> netDepositsEur = netDepositsEur.subtract(amountEur);
            case INTEREST -> interestEur = interestEur.add(amountEur);
            case FEE -> accountFeesEur = accountFeesEur.add(amountEur);
            case CONVERSION -> { /* traité plus haut */ }
        }
    }

    public void apply(Transaction tx) {
        String currency = iso(tx.getCurrency());
        boolean settledInCurrency = multiCurrency && !MoneyConstants.BASE_CURRENCY.equals(currency);
        BigDecimal amount = nz(settledInCurrency ? tx.getTotalAmount() : tx.getTotalAmountEur());
        BigDecimal fees = nz(settledInCurrency ? tx.getFees() : tx.getFeesEur());
        BigDecimal flow = switch (tx.getType()) {
            case BUY -> amount.add(fees).negate();
            case SELL, DIVIDEND -> amount.subtract(fees);
        };
        add(settledInCurrency ? currency : MoneyConstants.BASE_CURRENCY, flow);
    }

    /** Valeur en euros de tous les soldes, au taux fourni pour chaque devise. */
    public BigDecimal valueEur(Function<String, BigDecimal> rateToEur) {
        BigDecimal total = balanceEur;
        for (Map.Entry<String, BigDecimal> e : foreignBalances.entrySet()) {
            total = total.add(e.getValue().multiply(rateToEur.apply(e.getKey())));
        }
        return total.setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    public Map<String, BigDecimal> getForeignBalances() {
        return Collections.unmodifiableMap(foreignBalances);
    }

    private void add(String currency, BigDecimal amount) {
        if (MoneyConstants.BASE_CURRENCY.equals(currency)) {
            balanceEur = balanceEur.add(amount);
        } else {
            foreignBalances.merge(currency, amount, BigDecimal::add);
        }
    }

    private static String iso(String currency) {
        return currency == null || currency.isBlank() ? MoneyConstants.BASE_CURRENCY : currency.toUpperCase();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

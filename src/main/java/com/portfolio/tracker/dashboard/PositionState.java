package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.Transaction;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * État d'une position, construit en rejouant ses transactions dans l'ordre
 * chronologique. Source unique du calcul CUMP : utilisé par la valorisation
 * live ({@link PortfolioValuationService}) ET par la reconstruction jour par
 * jour de l'historique, ce qui garantit que la courbe et le dashboard donnent
 * les mêmes chiffres.
 *
 * Règles :
 * - BUY : augmente la quantité et le coût total (frais inclus dans le coût,
 * car ils font partie du prix de revient réel de la position).
 * - SELL : réduit la quantité proportionnellement au coût moyen courant.
 * Le gain réalisé = produit net de la vente - coût sorti.
 * - DIVIDEND : n'affecte ni quantité ni coût, alimente uniquement
 * dividendsEur.
 *
 * Classe mutable, non thread-safe : une instance par position et par calcul.
 */
@Getter
public class PositionState {

    private BigDecimal quantity = BigDecimal.ZERO;
    /** Coût total encore "en jeu" (EUR, au taux du jour de chaque achat). */
    private BigDecimal costBasisEur = BigDecimal.ZERO;
    private BigDecimal realizedEur = BigDecimal.ZERO;
    private BigDecimal dividendsEur = BigDecimal.ZERO;
    private BigDecimal feesEur = BigDecimal.ZERO;

    /** Dernier prix d'achat/vente saisi : repli quand aucun cours de marché n'existe. */
    private BigDecimal lastTradePrice;
    private String lastTradeCurrency;

    public void apply(Transaction tx) {
        feesEur = feesEur.add(nz(tx.getFeesEur()));

        switch (tx.getType()) {
            case BUY -> {
                quantity = quantity.add(tx.getQuantity());
                costBasisEur = costBasisEur.add(nz(tx.getTotalAmountEur())).add(nz(tx.getFeesEur()));
                rememberTradePrice(tx);
            }
            case SELL -> {
                BigDecimal sellQty = tx.getQuantity().min(quantity);
                if (quantity.signum() > 0 && sellQty.signum() > 0) {
                    BigDecimal avgCost = costBasisEur.divide(
                            quantity, MoneyConstants.QUANTITY_SCALE, MoneyConstants.ROUNDING);
                    BigDecimal costOut = avgCost.multiply(sellQty)
                            .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);

                    // Produit net de la vente = ce qui a été encaissé, frais déduits
                    BigDecimal proceedsEur = nz(tx.getTotalAmountEur()).subtract(nz(tx.getFeesEur()));

                    realizedEur = realizedEur.add(proceedsEur.subtract(costOut));
                    costBasisEur = costBasisEur.subtract(costOut);
                    quantity = quantity.subtract(sellQty);
                }
                // Si sellQty < tx.getQuantity() : vente à découvert non supportée,
                // on borne à la quantité détenue plutôt que de planter.
                rememberTradePrice(tx);
            }
            case DIVIDEND -> dividendsEur = dividendsEur.add(nz(tx.getTotalAmountEur()));
        }

        // Résidu d'arrondi : une position soldée ne doit pas garder un epsilon
        // de coût qui se reporterait sur un rachat ultérieur.
        if (quantity.signum() == 0) {
            costBasisEur = BigDecimal.ZERO;
        }
    }

    public boolean isOpen() {
        return quantity.signum() > 0;
    }

    public BigDecimal averageCostEur() {
        return isOpen()
                ? costBasisEur.divide(quantity, MoneyConstants.QUANTITY_SCALE, MoneyConstants.ROUNDING)
                : BigDecimal.ZERO;
    }

    private void rememberTradePrice(Transaction tx) {
        if (tx.getPricePerUnit() != null && tx.getPricePerUnit().signum() > 0) {
            lastTradePrice = tx.getPricePerUnit();
            lastTradeCurrency = tx.getCurrency();
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

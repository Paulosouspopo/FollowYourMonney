package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Liquidités : euros, devises, abondement")
class CashStateTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("Compte en euros : un achat en dollars est débité en euros au taux du jour de l'opération")
    void euroAccount() {
        CashState cash = new CashState();
        cash.apply(movement(CashMovementType.DEPOSIT, "1000", "EUR", "1"));
        cash.apply(tx(TransactionType.BUY, "500", "2", "USD", "0.9"));

        assertThat(cash.getBalanceEur()).isEqualByComparingTo("548.20"); // 1000 - 450 - 1.80
        assertThat(cash.getForeignBalances()).isEmpty();
        assertThat(cash.valueEur(c -> BigDecimal.ONE)).isEqualByComparingTo("548.20");
    }

    @Test
    @DisplayName("Compte multidevise : dépôt et achat en dollars restent en dollars, valorisés au taux fourni")
    void multiCurrency() {
        CashState cash = new CashState(true);
        cash.apply(movement(CashMovementType.DEPOSIT, "1000", "EUR", "1"));
        cash.apply(conversion("600", "EUR", "650", "USD"));
        cash.apply(movement(CashMovementType.DEPOSIT, "100", "USD", "0.9"));
        cash.apply(tx(TransactionType.BUY, "500", "2", "USD", "0.9"));
        cash.apply(tx(TransactionType.DIVIDEND, "10", "0", "USD", "0.9"));

        assertThat(cash.getBalanceEur()).isEqualByComparingTo("400");
        assertThat(cash.getForeignBalances()).containsExactly(Map.entry("USD", new BigDecimal("258")));
        // Apports : 1000 € + 100 $ au taux du jour du dépôt ; le change n'est pas un apport
        assertThat(cash.getNetDepositsEur()).isEqualByComparingTo("1090");
        assertThat(cash.valueEur(c -> new BigDecimal("0.95"))).isEqualByComparingTo("645.10"); // 400 + 258 × 0.95
    }

    @Test
    @DisplayName("Abondement : un apport (performance) suivi à part")
    void employerContribution() {
        CashState cash = new CashState();
        cash.apply(movement(CashMovementType.DEPOSIT, "500", "EUR", "1"));
        cash.apply(movement(CashMovementType.ABONDEMENT, "250", "EUR", "1"));
        assertThat(cash.getNetDepositsEur()).isEqualByComparingTo("750");
        assertThat(cash.getEmployerContributionsEur()).isEqualByComparingTo("250");
        assertThat(cash.getBalanceEur()).isEqualByComparingTo("750");
    }

    private static CashMovement movement(CashMovementType type, String amount, String currency, String rate) {
        return CashMovement.builder().type(type).amount(new BigDecimal(amount)).currency(currency)
                .exchangeRateToEur(new BigDecimal(rate)).movementDate(DAY).build();
    }

    private static CashMovement conversion(String amount, String currency, String counter, String counterCurrency) {
        return CashMovement.builder().type(CashMovementType.CONVERSION).amount(new BigDecimal(amount)).currency(currency)
                .counterAmount(new BigDecimal(counter)).counterCurrency(counterCurrency).movementDate(DAY).build();
    }

    private static Transaction tx(TransactionType type, String amount, String fees, String currency, String rate) {
        BigDecimal r = new BigDecimal(rate);
        return Transaction.builder().type(type).asset(Asset.builder().symbol("AAPL").build())
                .quantity(BigDecimal.ONE).pricePerUnit(new BigDecimal(amount))
                .totalAmount(new BigDecimal(amount)).fees(new BigDecimal(fees)).currency(currency).exchangeRateToEur(r)
                .totalAmountEur(new BigDecimal(amount).multiply(r)).feesEur(new BigDecimal(fees).multiply(r))
                .transactionDate(LocalDateTime.of(DAY, java.time.LocalTime.NOON)).build();
    }
}

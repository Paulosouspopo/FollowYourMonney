package com.portfolio.tracker.tax;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Moteur fiscal : PMP, report des moins-values, 150 VH bis")
class TaxEngineTest {

    private final Portfolio wallet = Portfolio.builder().id(UUID.randomUUID()).name("Wallet").build();
    private final Portfolio cto1 = Portfolio.builder().id(UUID.randomUUID()).name("CTO 1").build();
    private final Portfolio cto2 = Portfolio.builder().id(UUID.randomUUID()).name("CTO 2").build();

    private Transaction tx(Portfolio p, String symbol, TransactionType type, String qty, String amountEur, String fees,
            LocalDateTime when) {
        Asset asset = Asset.builder().id(UUID.nameUUIDFromBytes((p.getId() + symbol).getBytes())).symbol(symbol)
                .name(symbol).portfolio(p).build();
        return Transaction.builder().id(UUID.randomUUID()).asset(asset).type(type).quantity(new BigDecimal(qty))
                .totalAmountEur(new BigDecimal(amountEur)).feesEur(new BigDecimal(fees)).transactionDate(when).build();
    }

    private static LocalDateTime at(String date) {
        return LocalDate.parse(date).atTime(10, 0);
    }

    @Test
    @DisplayName("PMP tous comptes confondus, frais d'achat dans le coût, frais de vente déduits du prix")
    void pmp() {
        List<TaxEngine.SecuritySale> sales = TaxEngine.securitySales(List.of(
                tx(cto1, "AI.PA", TransactionType.BUY, "10", "1000", "10", at("2024-01-10")), // 101 €/titre
                tx(cto2, "AI.PA", TransactionType.BUY, "10", "2000", "10", at("2024-06-10")), // 201 €/titre
                tx(cto1, "AI.PA", TransactionType.SELL, "5", "1000", "5", at("2025-03-01"))));

        // PMP = (1010 + 2010) / 20 = 151 € ; coût 755 € ; prix net 995 € → +240 €
        assertThat(sales).singleElement().satisfies(s -> {
            assertThat(s.costEur()).isEqualByComparingTo("755");
            assertThat(s.proceedsEur()).isEqualByComparingTo("995");
            assertThat(s.gainEur()).isEqualByComparingTo("240");
        });
    }

    @Test
    @DisplayName("Moins-values reportées sur 10 ans, les plus anciennes d'abord")
    void report() {
        TreeMap<Integer, BigDecimal> net = new TreeMap<>(Map.of(
                2020, new BigDecimal("-1000"), 2022, new BigDecimal("-500"), 2025, new BigDecimal("1200")));
        BigDecimal[] r = TaxEngine.carryForward(net, 2025);
        assertThat(r[0]).isEqualByComparingTo("1200"); // imputé : 1000 (2020) + 200 (2022)
        assertThat(r[1]).isEqualByComparingTo("0");    // rien d'imposable
        assertThat(r[2]).isEqualByComparingTo("300");  // reste de 2022

        // 2014 : moins-value trop ancienne pour 2025 (plus de 10 ans)
        TreeMap<Integer, BigDecimal> old = new TreeMap<>(Map.of(2014, new BigDecimal("-1000"), 2025, new BigDecimal("400")));
        assertThat(TaxEngine.carryForward(old, 2025)[1]).isEqualByComparingTo("400");
    }

    @Test
    @DisplayName("150 VH bis : gain = C - PTA × C / V, PTA diminué après chaque cession")
    void crypto() {
        // Achats : 10 000 € de BTC ; vente de 3 000 € quand le portefeuille vaut 15 000 €
        List<Transaction> txs = List.of(
                tx(wallet, "BTC-EUR", TransactionType.BUY, "1", "10000", "0", at("2024-01-01")),
                tx(wallet, "BTC-EUR", TransactionType.SELL, "0.2", "3000", "0", at("2025-02-01")),
                tx(wallet, "BTC-EUR", TransactionType.SELL, "0.2", "3000", "0", at("2025-03-01")));
        List<TaxEngine.CryptoSale> sales = TaxEngine.cryptoSales(txs, (s, d) -> new BigDecimal("15000"));

        // 1re : part d'acquisition 10000 × 3000 / 15000 = 2000 → gain 1000 ; PTA restant 8000
        assertThat(sales.get(0).acquisitionShareEur()).isEqualByComparingTo("2000");
        assertThat(sales.get(0).gainEur()).isEqualByComparingTo("1000");
        // 2e : V = 0,8 × 15000 = 12000 ; part 8000 × 3000 / 12000 = 2000 → gain 1000
        assertThat(sales.get(1).portfolioValueEur()).isEqualByComparingTo("12000");
        assertThat(sales.get(1).gainEur()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Échange crypto contre crypto (vente + achat simultanés) : ni cession imposable, ni hausse du PTA")
    void echange() {
        List<Transaction> txs = List.of(
                tx(wallet, "BTC-EUR", TransactionType.BUY, "1", "10000", "0", at("2024-01-01")),
                tx(wallet, "BTC-EUR", TransactionType.SELL, "0.5", "7000", "0", at("2025-01-05")),
                tx(wallet, "ETH-EUR", TransactionType.BUY, "2", "7000", "0", LocalDate.parse("2025-01-05").atTime(10, 1)),
                tx(wallet, "ETH-EUR", TransactionType.SELL, "2", "8000", "0", at("2025-06-01")));
        List<TaxEngine.CryptoSale> sales = TaxEngine.cryptoSales(txs, (s, d) -> s.equals("BTC-EUR")
                ? new BigDecimal("14000") : new BigDecimal("4000"));

        // Seule la vente d'ETH contre euros compte ; PTA = 10 000 (l'échange ne l'augmente pas)
        // V = 0,5 × 14000 + 2 × 4000 = 15000 ; part = 10000 × 8000 / 15000 = 5333,33
        assertThat(sales).singleElement().satisfies(s -> {
            assertThat(s.symbol()).isEqualTo("ETH-EUR");
            assertThat(s.portfolioValueEur()).isEqualByComparingTo("15000");
            assertThat(s.acquisitionShareEur()).isEqualByComparingTo("5333.33");
            assertThat(s.gainEur()).isEqualByComparingTo("2666.67");
        });
    }
}

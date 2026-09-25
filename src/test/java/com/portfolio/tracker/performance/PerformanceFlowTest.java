package com.portfolio.tracker.performance;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.assetprice.AssetPriceService;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.dashboard.DashboardService;
import com.portfolio.tracker.dashboard.dto.DashboardResponse;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.performance.dto.PerformanceResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.snapshot.PortfolioHistoryService;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Performance à partir des vrais snapshots. Yahoo simulé : PERF cote 100 € jusqu'à
 * avant-hier, 105 € hier, 110 € aujourd'hui ; la paire EURUSD=X vaut 1,10.
 */
@DisplayName("Performance — flux des snapshots, TWR, indice, devise d'affichage")
class PerformanceFlowTest extends AbstractIntegrationTest {

    @Autowired private PerformanceService performanceService;
    @Autowired private DashboardService dashboardService;
    @Autowired private TransactionService transactionService;
    @Autowired private CashMovementService cashMovementService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;
    @Autowired private PortfolioHistoryService historyService;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;
    @Autowired private AssetPriceService assetPriceService;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .email("perf-" + UUID.randomUUID() + "@fym.io")
                .username("perf-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .emailVerified(true)
                .build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("PERF")).thenReturn(Optional.of(new MarketQuote("PERF", new BigDecimal("110"),
                "EUR", LocalDateTime.now(), today, "Perf Index", "Paris", "ETF")));
        when(marketDataProvider.getQuote("EURUSD=X")).thenReturn(Optional.of(new MarketQuote("EURUSD=X",
                new BigDecimal("1.10"), "USD", LocalDateTime.now(), today, "EUR/USD", "CCY", "CURRENCY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            String symbol = inv.getArgument(0);
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                BigDecimal price = symbol.equals("EURUSD=X") ? new BigDecimal("1.10")
                        : d.equals(today.minusDays(1)) ? new BigDecimal("105") : new BigDecimal("100");
                points.add(new MarketPricePoint(symbol, price, symbol.equals("EURUSD=X") ? "USD" : "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Compte sans suivi : achat = apport, TWR +10 % du cours, indice identique, gain 100 €")
    void compteSansSuivi() {
        UUID pf = portfolio("PEA", false);
        buy(pf, 10, 5);

        PortfolioSnapshot buyDay = snapshot(pf, today.minusDays(5));
        assertThat(buyDay.getNetFlow()).isEqualByComparingTo("1000");
        assertThat(snapshot(pf, today.minusDays(4)).getNetFlow()).isEqualByComparingTo("0");

        PerformanceResponse r = performanceService.performance(user.getId(), pf, "all", "PERF");
        assertThat(r.twrPct()).isEqualByComparingTo("10");
        assertThat(r.netFlowsEur()).isEqualByComparingTo("1000");
        assertThat(r.gainEur()).isEqualByComparingTo("100");
        assertThat(r.twrAnnualizedPct()).isNull(); // moins d'un an : pas d'annualisation
        assertThat(r.benchmark().returnPct()).isEqualByComparingTo("10");
        assertThat(r.series()).last().satisfies(p -> {
            assertThat(p.twrPct()).isEqualByComparingTo("10");
            assertThat(p.benchmarkPct()).isEqualByComparingTo("10");
        });
        // Hier +5 % : la courbe suit le cours
        assertThat(r.series().get(r.series().size() - 2).twrPct()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Compte suivi : un versement n'est pas de la performance ; vue globale et détail par portefeuille")
    void versementNeutre() {
        UUID pf = portfolio("CTO", true);
        cashMovementService.create(pf, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("1000"),
                today.minusDays(6), null), user.getId());
        buy(pf, 10, 5);
        cashMovementService.create(pf, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("5000"),
                today.minusDays(2), null), user.getId());

        assertThat(snapshot(pf, today.minusDays(5)).getNetFlow()).isEqualByComparingTo("0"); // achat = mouvement interne
        assertThat(snapshot(pf, today.minusDays(2)).getNetFlow()).isEqualByComparingTo("5000");

        PerformanceResponse r = performanceService.performance(user.getId(), null, "all", null);
        // 1000 € investis en PERF (+10 %), 5000 € de liquidités qui ne bougent pas
        assertThat(r.gainEur()).isEqualByComparingTo("100");
        assertThat(r.netFlowsEur()).isEqualByComparingTo("6000");
        assertThat(r.twrPct()).isBetween(new BigDecimal("1.5"), new BigDecimal("10")); // dilué par les liquidités, jamais +500 %
        assertThat(r.portfolios()).singleElement().satisfies(p -> assertThat(p.name()).isEqualTo("CTO"));
        assertThat(r.benchmark()).isNull();
    }

    @Test
    @DisplayName("Achat sans versement sur un compte suivi : découvert = apport implicite, pas une perte de 100 %")
    void decouvert() {
        UUID pf = portfolio("CTO", true);
        buy(pf, 10, 5);

        PortfolioSnapshot buyDay = snapshot(pf, today.minusDays(5));
        assertThat(buyDay.getTotalValue()).isEqualByComparingTo("0"); // 1000 € de titres, -1000 € de liquidités
        assertThat(buyDay.getPerformanceValue()).isEqualByComparingTo("1000");
        assertThat(buyDay.getNetFlow()).isEqualByComparingTo("1000");
        assertThat(performanceService.performance(user.getId(), pf, "all", null).twrPct()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Snapshots antérieurs aux flux : le rattrapage recalcule le portefeuille entièrement")
    void rattrapage() {
        UUID pf = portfolio("PEA", false);
        buy(pf, 10, 5);
        tx.executeWithoutResult(s -> snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(pf)
                .forEach(snap -> snap.setNetFlow(null)));

        historyService.catchUp();

        assertThat(snapshot(pf, today.minusDays(5)).getNetFlow()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Courbe en dollars : chaque jour au taux de ce jour-là")
    void courbeEnDollars() {
        UUID pf = portfolio("PEA", false);
        buy(pf, 10, 5);

        DashboardResponse usd = dashboardService.getPortfolioDashboard(user.getId(), pf, "7d", "usd");
        assertThat(usd.getCurveCurrency()).isEqualTo("USD");
        assertThat(usd.getCurve().get(usd.getCurve().size() - 2).getTotalValueEur()).isEqualByComparingTo("1155"); // 1050 × 1,10
        assertThat(usd.getTotalValueEur()).isEqualByComparingTo("1100"); // les totaux restent en EUR
    }

    @Test
    @DisplayName("Tuiles : tendance 30 jours par portefeuille, variation de plus-value (pas des versements)")
    void tendanceDesTuiles() {
        UUID pf = portfolio("PEA", false);
        buy(pf, 10, 5);

        DashboardResponse d = dashboardService.getDashboard(user.getId(), "30d", null);
        assertThat(d.getTrends()).singleElement().satisfies(t -> {
            assertThat(t.portfolioId()).isEqualTo(pf);
            assertThat(t.values()).hasSize(6).last().satisfies(v -> assertThat(v).isEqualByComparingTo("1100"));
            assertThat(t.changeEur()).isEqualByComparingTo("100"); // 1000 € investis, +10 % du cours : l'achat n'est pas un gain
            assertThat(t.changePct()).isEqualByComparingTo("10");
        });
    }

    private UUID portfolio(String name, boolean cashTracking) {
        return portfolioRepository.save(Portfolio.builder().name(name)
                .type(name.equals("PEA") ? PortfolioType.PEA : PortfolioType.CTO)
                .user(user).cashTracking(cashTracking).build()).getId();
    }

    private void buy(UUID portfolioId, int quantity, int daysAgo) {
        transactionService.create(portfolioId, new TransactionCreateRequest("PERF", TransactionType.BUY,
                BigDecimal.valueOf(quantity), new BigDecimal("100"), null, "EUR",
                today.minusDays(daysAgo).atTime(10, 0), null), user.getId());
        // Comme le job horaire : cotation du jour (110 €), puis point du jour
        assetPriceService.updateAllAssetPrices();
        historyService.rebuildAll(today);
    }

    private PortfolioSnapshot snapshot(UUID portfolioId, LocalDate day) {
        return tx.execute(s -> snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId).stream()
                .filter(snap -> snap.getSnapshotDate().equals(day)).findFirst().orElseThrow());
    }
}

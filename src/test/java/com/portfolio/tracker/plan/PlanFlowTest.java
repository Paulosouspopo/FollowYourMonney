package com.portfolio.tracker.plan;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.plan.dto.PlanRequest;
import com.portfolio.tracker.plan.dto.PlanResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Investissements programmés : exécution des échéances, reprise, règles. Yahoo simulé. */
@DisplayName("Investissements programmés — parcours complet")
class PlanFlowTest extends AbstractIntegrationTest {

    @Autowired private PlanService planService;
    @Autowired private PlanExecutor executor;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CashMovementRepository cashMovementRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .email("plan-" + UUID.randomUUID() + "@fym.io")
                .username("plan-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .emailVerified(true)
                .build());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("FAKE")).thenReturn(Optional.of(new MarketQuote("FAKE", new BigDecimal("100"),
                "EUR", LocalDateTime.now(), today, "Fake ETF", "Paris", "ETF")));
        // FAKE cote 100 € chaque jour
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            if (!"FAKE".equals(inv.getArgument(0))) {
                return List.of();
            }
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)); d = d.plusDays(1)) {
                points.add(new MarketPricePoint("FAKE", new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Plan démarré il y a 2 mois : 3 échéances créées au cours du jour, idempotent")
    void rattrapageEtIdempotence() {
        UUID pf = portfolio(PortfolioType.PEA, false);
        LocalDate start = today.minusMonths(2);

        PlanResponse plan = planService.create(pf, buy(new BigDecimal("100"), new BigDecimal("1"), start, true), user.getId());

        List<Transaction> txs = transactionRepository.findByPortfolioIdAndUserId(pf, user.getId());
        assertThat(txs).hasSize(3);
        assertThat(txs).allSatisfy(t -> {
            assertThat(t.getQuantity()).isEqualByComparingTo("0.99"); // (100 - 1 de frais) / 100
            assertThat(t.getFees()).isEqualByComparingTo("1.00");
            assertThat(t.getExternalRef()).startsWith("PLAN:" + plan.id());
        });
        assertThat(plan.occurrences()).isEqualTo(3);
        assertThat(plan.lastExecutionDate()).isEqualTo(today);
        assertThat(plan.nextExecutionDate()).isEqualTo(PlanFrequency.MONTHLY.occurrence(start, 3));
        assertThat(plan.monthlyAmount()).isEqualByComparingTo("100.00");

        // Nouveau passage (job du soir) : rien de plus
        executor.runDuePlans();
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId())).hasSize(3);
    }

    @Test
    @DisplayName("Parts entières : quantité arrondie à l'unité, échéance sautée si le montant ne suffit pas")
    void partsEntieres() {
        UUID pf = portfolio(PortfolioType.PEA, false);
        planService.create(pf, buy(new BigDecimal("250"), null, today, false), user.getId());
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId()))
                .singleElement().satisfies(t -> assertThat(t.getQuantity()).isEqualByComparingTo("2"));

        UUID other = portfolio(PortfolioType.PEA, false);
        PlanResponse small = planService.create(other, buy(new BigDecimal("50"), null, today, false), user.getId());
        assertThat(transactionRepository.findByPortfolioIdAndUserId(other, user.getId())).isEmpty();
        assertThat(small.lastError()).contains("sautée");
        assertThat(small.nextExecutionDate()).isAfter(today);
    }

    @Test
    @DisplayName("Versement programmé sur un livret ; refusé sur un compte sans suivi des liquidités")
    void versements() {
        UUID livret = portfolio(PortfolioType.LIVRET, true);
        planService.create(livret, deposit(new BigDecimal("50"), today.minusWeeks(3)), user.getId());
        assertThat(cashMovementRepository.findByPortfolioIdAndUserId(livret, user.getId())).hasSize(4);

        UUID cto = portfolio(PortfolioType.CTO, false);
        assertThatThrownBy(() -> planService.create(cto, deposit(new BigDecimal("50"), today), user.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("suivi des liquidités");
        assertThatThrownBy(() -> planService.create(livret, buy(new BigDecimal("50"), null, today, true), user.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("livret");
    }

    @Test
    @DisplayName("Reprise après une pause : la période de pause n'est pas rattrapée")
    void pauseEtReprise() {
        UUID pf = portfolio(PortfolioType.CTO, false);
        LocalDate start = today.minusWeeks(6).plusDays(1); // aucune échéance tombe aujourd'hui
        PlanRequest paused = new PlanRequest(InvestmentPlan.Type.BUY, "FAKE", new BigDecimal("100"), null,
                PlanFrequency.WEEKLY, start, null, true, false);

        PlanResponse plan = planService.create(pf, paused, user.getId());
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId())).isEmpty();

        PlanResponse resumed = planService.update(plan.id(), new PlanRequest(InvestmentPlan.Type.BUY, "FAKE",
                new BigDecimal("100"), null, PlanFrequency.WEEKLY, start, null, true, true), user.getId());
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId())).isEmpty();
        assertThat(resumed.nextExecutionDate()).isAfterOrEqualTo(today);
    }

    @Test
    @DisplayName("Après une échéance, l'actif et la fréquence sont figés ; le montant reste modifiable")
    void structureFigee() {
        UUID pf = portfolio(PortfolioType.CTO, false);
        PlanResponse plan = planService.create(pf, buy(new BigDecimal("100"), null, today, true), user.getId());

        assertThatThrownBy(() -> planService.update(plan.id(), new PlanRequest(InvestmentPlan.Type.BUY, "FAKE",
                new BigDecimal("100"), null, PlanFrequency.WEEKLY, today, null, true, true), user.getId()))
                .isInstanceOf(BadRequestException.class);

        PlanResponse updated = planService.update(plan.id(), new PlanRequest(InvestmentPlan.Type.BUY, "FAKE",
                new BigDecimal("200"), null, PlanFrequency.MONTHLY, today, null, true, true), user.getId());
        assertThat(updated.amount()).isEqualByComparingTo("200");
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Cours introuvable : l'erreur est notée et l'échéance reste à exécuter")
    void coursIndisponible() {
        UUID pf = portfolio(PortfolioType.CTO, false);
        when(marketDataProvider.getQuote("NOHIST")).thenReturn(Optional.of(new MarketQuote("NOHIST", new BigDecimal("10"),
                "EUR", LocalDateTime.now(), today, "Sans historique", "Paris", "EQUITY")));

        PlanResponse plan = planService.create(pf, new PlanRequest(InvestmentPlan.Type.BUY, "NOHIST", new BigDecimal("100"),
                null, PlanFrequency.MONTHLY, today.minusMonths(1), null, true, true), user.getId());

        assertThat(plan.lastError()).contains("indisponible");
        assertThat(plan.nextExecutionDate()).isEqualTo(today.minusMonths(1));
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId())).isEmpty();
    }

    // ------------------------------------------------------------------ utils

    private PlanRequest buy(BigDecimal amount, BigDecimal fees, LocalDate start, boolean fractional) {
        return new PlanRequest(InvestmentPlan.Type.BUY, "FAKE", amount, fees, PlanFrequency.MONTHLY, start, null,
                fractional, true);
    }

    private PlanRequest deposit(BigDecimal amount, LocalDate start) {
        return new PlanRequest(InvestmentPlan.Type.DEPOSIT, null, amount, null, PlanFrequency.WEEKLY, start, null, true, true);
    }

    private UUID portfolio(PortfolioType type, boolean cashTracking) {
        return portfolioRepository.save(Portfolio.builder().name(type + "-" + UUID.randomUUID()).type(type).user(user)
                .cashTracking(cashTracking).build()).getId();
    }
}

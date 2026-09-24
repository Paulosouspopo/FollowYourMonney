package com.portfolio.tracker.cash;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.cash.dto.CashMovementResponse;
import com.portfolio.tracker.dashboard.DashboardService;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.AllocationSliceDTO;
import com.portfolio.tracker.dashboard.dto.DashboardResponse;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.portfolio.PortfolioService;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.portfolio.dto.PortfolioResponse;
import com.portfolio.tracker.portfolio.dto.PortfolioUpdateRequest;
import com.portfolio.tracker.shared.exception.BadRequestException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Livrets et mouvements d'argent : règles, courbe et dashboard, via les vrais services. */
@DisplayName("Liquidités — livrets et mouvements d'argent")
class CashMovementFlowTest extends AbstractIntegrationTest {

    @Autowired private CashMovementService cashMovementService;
    @Autowired private PortfolioService portfolioService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioValuationService valuationService;
    @Autowired private DashboardService dashboardService;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private UUID userId;

    @BeforeEach
    void seed() {
        userId = userRepository.save(User.builder()
                .email("cash-" + UUID.randomUUID() + "@fym.io")
                .username("cash-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .emailVerified(true)
                .build()).getId();
    }

    @Test
    @DisplayName("Livret : suivi des liquidités forcé, courbe depuis le premier versement, point du jour = dashboard")
    void livret() {
        PortfolioResponse livret = createPortfolio("Livret A", PortfolioType.LIVRET, null);
        assertThat(livret.cashTracking()).isTrue();

        add(livret.id(), CashMovementType.DEPOSIT, "1000", 10);
        add(livret.id(), CashMovementType.INTEREST, "15", 2);

        List<PortfolioSnapshot> curve = snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(livret.id());
        assertThat(curve).hasSize(11);
        assertThat(at(curve, today.minusDays(5)).getTotalValue()).isEqualByComparingTo("1000.00");
        assertThat(at(curve, today).getTotalValue()).isEqualByComparingTo("1015.00");

        PortfolioValuation valuation = valuationService.valuate(userId, livret.id(), null).getPortfolios().get(0);
        assertThat(valuation.getCurrentValueEur()).isEqualByComparingTo(at(curve, today).getTotalValue());
        assertThat(valuation.getInterestEur()).isEqualByComparingTo("15.00");

        DashboardResponse dashboard = dashboardService.getDashboard(userId, "30d");
        assertThat(dashboard.getCashEur()).isEqualByComparingTo("1015.00");
        assertThat(dashboard.getAllocation()).extracting(AllocationSliceDTO::getCategory).containsExactly("LIVRET");
    }

    @Test
    @DisplayName("Livret : un retrait (ou une suppression de versement) qui mettrait le solde à découvert est refusé")
    void livretJamaisADecouvert() {
        UUID livret = createPortfolio("Livret A", PortfolioType.LIVRET, null).id();
        CashMovementResponse deposit = add(livret, CashMovementType.DEPOSIT, "500", 10);
        add(livret, CashMovementType.WITHDRAWAL, "300", 5);

        assertThatThrownBy(() -> add(livret, CashMovementType.WITHDRAWAL, "300", 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Solde insuffisant");
        assertThatThrownBy(() -> cashMovementService.delete(livret, deposit.id(), userId))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Livret : aucune transaction sur actif coté ; type non convertible s'il détient des actifs")
    void livretSansActifs() {
        UUID livret = createPortfolio("Livret A", PortfolioType.LIVRET, null).id();

        assertThatThrownBy(() -> transactionService.create(livret, new TransactionCreateRequest("AAPL",
                TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN, null, "EUR", today.atStartOfDay(), null), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("livret");
    }

    @Test
    @DisplayName("Compte sans suivi des liquidités : mouvements refusés ; activer le suivi recalcule l'historique")
    void activationDuSuivi() {
        PortfolioResponse cto = createPortfolio("CTO", PortfolioType.CTO, null);
        assertThat(cto.cashTracking()).isFalse();
        assertThatThrownBy(() -> add(cto.id(), CashMovementType.DEPOSIT, "100", 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("suivi des liquidités");

        portfolioService.update(cto.id(), new PortfolioUpdateRequest("CTO", null, PortfolioType.CTO, true, null), userId);
        add(cto.id(), CashMovementType.DEPOSIT, "2000", 4);

        PortfolioValuation valuation = valuationService.valuate(userId, cto.id(), null).getPortfolios().get(0);
        assertThat(valuation.getCashEur()).isEqualByComparingTo("2000.00");
        assertThat(valuation.getNetDepositsEur()).isEqualByComparingTo("2000.00");
        assertThat(at(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(cto.id()), today).getTotalValue())
                .isEqualByComparingTo("2000.00");

        // Désactiver le suivi : liquidités ignorées, historique recalculé (plus aucun événement)
        portfolioService.update(cto.id(), new PortfolioUpdateRequest("CTO", null, PortfolioType.CTO, false, null), userId);
        assertThat(valuationService.valuate(userId, cto.id(), null).getTotalValueEur()).isEqualByComparingTo("0.00");
        assertThat(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(cto.id())).isEmpty();
    }

    @Test
    @DisplayName("Modification d'un mouvement déplacé dans le temps : courbe recalculée depuis la date la plus ancienne")
    void modificationDeplacee() {
        UUID livret = createPortfolio("LDDS", PortfolioType.LIVRET, null).id();
        CashMovementResponse deposit = add(livret, CashMovementType.DEPOSIT, "800", 3);

        cashMovementService.update(livret, deposit.id(),
                new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("900"), today.minusDays(6), null), userId);

        List<PortfolioSnapshot> curve = snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(livret);
        assertThat(curve).hasSize(7);
        assertThat(curve).allSatisfy(s -> assertThat(s.getTotalValue()).isEqualByComparingTo("900.00"));
    }

    // ------------------------------------------------------------------ utils

    private PortfolioResponse createPortfolio(String name, PortfolioType type, Boolean cashTracking) {
        return portfolioService.create(new PortfolioCreateRequest(name, null, type, cashTracking, null), userId);
    }

    private CashMovementResponse add(UUID portfolioId, CashMovementType type, String amount, int daysAgo) {
        return cashMovementService.create(portfolioId,
                new CashMovementRequest(type, new BigDecimal(amount), today.minusDays(daysAgo), null), userId);
    }

    private static PortfolioSnapshot at(List<PortfolioSnapshot> curve, LocalDate day) {
        return curve.stream().filter(s -> s.getSnapshotDate().equals(day)).findFirst()
                .orElseThrow(() -> new AssertionError("Pas de snapshot au " + day));
    }
}

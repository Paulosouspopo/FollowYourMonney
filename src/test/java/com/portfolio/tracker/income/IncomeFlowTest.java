package com.portfolio.tracker.income;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.income.dto.IncomeResponse;
import com.portfolio.tracker.marketdata.DividendEvent;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** DIV.PA cote 100 € et verse 1 € par trimestre ; un livret à 3 %. */
@DisplayName("Revenus passifs")
class IncomeFlowTest extends AbstractIntegrationTest {

    @Autowired private IncomeService incomeService;
    @Autowired private TransactionService transactionService;
    @Autowired private CashMovementService cashMovementService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("inc-" + UUID.randomUUID() + "@fym.io")
                .username("inc-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("DIV.PA")).thenReturn(Optional.of(new MarketQuote("DIV.PA", new BigDecimal("100"),
                "EUR", LocalDateTime.now(), today, "Dividend Corp", "Paris", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
        when(marketDataProvider.getDividends(eq("DIV.PA"), any(), any())).thenReturn(List.of(
                div(today.minusDays(20)), div(today.minusDays(110)), div(today.minusDays(200)), div(today.minusDays(290))));
    }

    @Test
    @DisplayName("Projection (dividendes + livret), rendement sur prix de revient, reçu du mois, calendrier")
    void revenus() {
        UUID pea = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
        transactionService.create(pea, new TransactionCreateRequest("DIV.PA", TransactionType.BUY, BigDecimal.TEN,
                new BigDecimal("100"), null, "EUR", today.minusDays(300).atTime(10, 0), null), user.getId());
        transactionService.create(pea, new TransactionCreateRequest("DIV.PA", TransactionType.DIVIDEND, BigDecimal.ONE,
                BigDecimal.TEN, null, "EUR", today.atStartOfDay(), null), user.getId());

        UUID livret = portfolioRepository.save(Portfolio.builder().name("Livret A").type(PortfolioType.LIVRET).user(user)
                .cashTracking(true).annualInterestRate(new BigDecimal("3")).build()).getId();
        cashMovementService.create(livret, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("1000"),
                today.minusDays(100), null), user.getId());

        IncomeResponse r = incomeService.income(user.getId());
        // 4 € par action × 10 = 40 € ; livret 1000 € à 3 % = 30 €
        assertThat(r.annualProjectedEur()).isEqualByComparingTo("70");
        assertThat(r.monthlyProjectedEur()).isEqualByComparingTo("5.83");
        assertThat(r.yieldOnCostPct()).isEqualByComparingTo("4"); // 40 € / 1000 € investis
        assertThat(r.positions()).extracting(IncomeResponse.PositionIncome::kind).containsExactly("DIVIDEND", "INTEREST");
        assertThat(r.positions().get(0).paymentsPerYear()).isEqualTo(4);
        assertThat(r.receivedThisYearEur()).isEqualByComparingTo("10");
        assertThat(r.received()).hasSize(24).last().satisfies(m -> assertThat(m.dividendsEur()).isEqualByComparingTo("10"));
        assertThat(r.upcoming()).isNotEmpty().allSatisfy(u -> assertThat(u.date()).isAfterOrEqualTo(today));
    }

    private DividendEvent div(LocalDate date) {
        return new DividendEvent("DIV.PA", date, BigDecimal.ONE, "EUR");
    }
}

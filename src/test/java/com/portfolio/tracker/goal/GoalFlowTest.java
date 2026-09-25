package com.portfolio.tracker.goal;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.goal.dto.GoalRequest;
import com.portfolio.tracker.goal.dto.GoalResponse;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.exception.BadRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** GOAL.PA cote 100 € ; 20 actions dans un PEA = 2000 €. */
@DisplayName("Objectifs d'épargne")
class GoalFlowTest extends AbstractIntegrationTest {

    @Autowired private GoalService goalService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;
    private UUID pea;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("goal-" + UUID.randomUUID() + "@fym.io")
                .username("goal-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("GOAL.PA")).thenReturn(Optional.of(new MarketQuote("GOAL.PA", new BigDecimal("100"),
                "EUR", LocalDateTime.now(), today, "Goal Corp", "Paris", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
        pea = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
        transactionService.create(pea, new TransactionCreateRequest("GOAL.PA", TransactionType.BUY, new BigDecimal("20"),
                new BigDecimal("100"), null, "EUR", today.minusDays(10).atTime(10, 0), null), user.getId());
    }

    @Test
    @DisplayName("Progression sur le patrimoine ou un portefeuille ; modification et suppression")
    void progression() {
        GoalResponse all = goalService.create(new GoalRequest("Apport immobilier", new BigDecimal("20000"),
                today.plusYears(3), null), user.getId());
        assertThat(all.currentValueEur()).isEqualByComparingTo("2000");
        assertThat(all.progressPct()).isEqualByComparingTo("10");
        assertThat(all.portfolioName()).isNull();

        GoalResponse pf = goalService.create(new GoalRequest("PEA à 1000 €", new BigDecimal("1000"), null, pea), user.getId());
        assertThat(pf.progressPct()).isEqualByComparingTo("100"); // dépassé : plafonné
        assertThat(pf.portfolioName()).isEqualTo("PEA");

        GoalResponse updated = goalService.update(all.id(), new GoalRequest("Apport", new BigDecimal("4000"), null, null),
                user.getId());
        assertThat(updated.progressPct()).isEqualByComparingTo("50");

        goalService.delete(pf.id(), user.getId());
        assertThat(goalService.findAll(user.getId())).extracting(GoalResponse::name).containsExactly("Apport");
    }

    @Test
    @DisplayName("Échéance passée refusée")
    void validation() {
        assertThatThrownBy(() -> goalService.create(new GoalRequest("Trop tard", new BigDecimal("1000"),
                today.minusDays(1), null), user.getId())).isInstanceOf(BadRequestException.class);
    }
}

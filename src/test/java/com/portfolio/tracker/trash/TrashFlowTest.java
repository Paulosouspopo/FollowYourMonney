package com.portfolio.tracker.trash;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioService;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.transaction.TransactionRepository;
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

@DisplayName("Corbeille : suppression puis restauration")
class TrashFlowTest extends AbstractIntegrationTest {

    @Autowired private TrashService trashService;
    @Autowired private PortfolioService portfolioService;
    @Autowired private TransactionService transactionService;
    @Autowired private CashMovementService cashMovementService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PortfolioValuationService valuationService;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("trash-" + UUID.randomUUID() + "@fym.io")
                .username("trash-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("TTE.PA")).thenReturn(Optional.of(new MarketQuote("TTE.PA", new BigDecimal("60"),
                "EUR", LocalDateTime.now(), today, "TotalEnergies", "Paris", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("60"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Une opération supprimée revient à l'identique ; un portefeuille revient avec ses opérations et mouvements")
    void restore() {
        UUID pf = portfolioService.create(new PortfolioCreateRequest("CTO", null, PortfolioType.CTO, true, null),
                user.getId()).id();
        cashMovementService.create(pf, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("1000"),
                today.minusDays(30), null), user.getId());
        UUID tx = transactionService.create(pf, new TransactionCreateRequest("TTE.PA", TransactionType.BUY, BigDecimal.TEN,
                new BigDecimal("50"), BigDecimal.ONE, "EUR", today.minusDays(20).atTime(10, 0), "note"), user.getId()).id();
        BigDecimal before = value(pf);

        UUID trashed = transactionService.deleteById(tx, user.getId());
        assertThat(trashService.list(user.getId())).singleElement()
                .satisfies(i -> assertThat(i.label()).startsWith("Achat · TotalEnergies"));
        assertThat(trashService.restore(trashed, user.getId())).isEqualTo(pf);
        assertThat(transactionRepository.findByPortfolioIdAndUserId(pf, user.getId())).singleElement()
                .satisfies(t -> assertThat(t.getNotes()).isEqualTo("note"));
        assertThat(value(pf)).isEqualByComparingTo(before);
        assertThat(trashService.list(user.getId())).isEmpty();

        UUID trashedPortfolio = portfolioService.deleteById(pf, user.getId());
        assertThat(portfolioRepository.findById(pf)).isEmpty();
        UUID restored = trashService.restore(trashedPortfolio, user.getId());
        assertThat(value(restored)).isEqualByComparingTo(before); // 1000 - 501 + 10 × 60
    }

    @Test
    @DisplayName("Une opération dont le portefeuille a disparu ne se restaure pas seule")
    void orphan() {
        UUID pf = portfolioService.create(new PortfolioCreateRequest("PEA", null, PortfolioType.PEA, false, null),
                user.getId()).id();
        UUID tx = transactionService.create(pf, new TransactionCreateRequest("TTE.PA", TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal("50"), null, "EUR", today.minusDays(5).atTime(10, 0), null), user.getId()).id();
        UUID trashedTx = transactionService.deleteById(tx, user.getId());
        portfolioService.deleteById(pf, user.getId());
        assertThatThrownBy(() -> trashService.restore(trashedTx, user.getId())).isInstanceOf(BadRequestException.class);
    }

    private BigDecimal value(UUID portfolioId) {
        return valuationService.valuate(user.getId(), portfolioId, null).getPortfolios().get(0).getCurrentValueEur();
    }
}

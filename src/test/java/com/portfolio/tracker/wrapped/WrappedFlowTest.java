package com.portfolio.tracker.wrapped;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.performance.dto.PerformanceResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
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
import static org.mockito.Mockito.when;

@DisplayName("Bilan de l'année")
class WrappedFlowTest extends AbstractIntegrationTest {

    @Autowired private WrappedService wrappedService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("Année écoulée : performance, mois par mois, ligne star, opérations et profil")
    void lastYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear() - 1;
        User user = userRepository.save(User.builder().email("wr-" + UUID.randomUUID() + "@fym.io")
                .username("wr-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("UP.PA")).thenReturn(Optional.of(new MarketQuote("UP.PA", new BigDecimal("200"),
                "EUR", LocalDateTime.now(), today, "Hausse SA", "Paris", "EQUITY")));
        // Le cours double sur l'année écoulée : 100 € au 1er janvier, 200 € au 31 décembre
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                double progress = d.getYear() < year ? 0 : d.getYear() > year ? 1 : (d.getDayOfYear() - 1) / 364.0;
                points.add(new MarketPricePoint(inv.getArgument(0), BigDecimal.valueOf(100 + 100 * Math.min(progress, 1)),
                        "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
        UUID pea = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
        transactionService.create(pea, new TransactionCreateRequest("UP.PA", TransactionType.BUY, BigDecimal.TEN,
                new BigDecimal("100"), null, "EUR", LocalDate.of(year - 1, 12, 15).atTime(10, 0), null), user.getId());
        for (int m = 1; m <= 10; m++) {
            transactionService.create(pea, new TransactionCreateRequest("UP.PA", TransactionType.BUY, BigDecimal.ONE,
                    BigDecimal.valueOf(100 + 100 * (m - 1) / 12.0), null, "EUR", LocalDate.of(year, m, 3).atTime(10, 0), null),
                    user.getId());
        }

        WrappedService.Wrapped w = wrappedService.wrapped(user.getId(), year);
        assertThat(w.year()).isEqualTo(year);
        assertThat(w.complete()).isTrue();
        assertThat(w.twrPct()).isBetween(95.0, 101.0);
        assertThat(w.months()).hasSize(12).doesNotContainNull();
        assertThat(w.bestLine().symbol()).isEqualTo("UP.PA");
        assertThat(w.buys()).isEqualTo(10);
        assertThat(w.activeMonths()).isEqualTo(10);
        assertThat(w.newAssets()).isZero(); // déjà détenue avant l'année
        assertThat(w.personality().key()).isEqualTo("METRONOME");
        assertThat(w.years()).contains(year - 1, year, today.getYear());
    }

    @Test
    @DisplayName("Mois par mois depuis la performance cumulée")
    void monthly() {
        Double[] months = WrappedService.monthly(List.of(
                new PerformanceResponse.Point(LocalDate.of(2025, 1, 31), null, new BigDecimal("10"), null),
                new PerformanceResponse.Point(LocalDate.of(2025, 2, 28), null, new BigDecimal("21"), null)));
        assertThat(months[0]).isEqualTo(10.0);
        assertThat(months[1]).isEqualTo(10.0);
        assertThat(months[2]).isNull();
    }
}

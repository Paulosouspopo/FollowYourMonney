package com.portfolio.tracker.demo;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.auth.AuthService;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("Mode démo")
class DemoFlowTest extends AbstractIntegrationTest {

    @Autowired private DemoService demoService;
    @Autowired private UserRepository userRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("Un compte invité rempli sur trois ans, connecté aussitôt, supprimé après 24 h")
    void demo() {
        LocalDate today = LocalDate.now();
        when(marketDataProvider.getQuote(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            if (s.endsWith("=X")) {
                return Optional.empty();
            }
            String type = s.endsWith("-EUR") ? "CRYPTOCURRENCY" : s.equals("CW8.PA") || s.equals("PUST.PA") ? "ETF" : "EQUITY";
            return Optional.of(new MarketQuote(s, new BigDecimal("100"), "EUR", LocalDateTime.now(), today, s, "Paris", type));
        });
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });

        AuthService.Session session = demoService.start(AuthService.Device.UNKNOWN);
        assertThat(session.accessToken()).isNotBlank();
        User demo = userRepository.findAll().stream().filter(User::isDemo).findFirst().orElseThrow();
        assertThat(demo.getEmail()).endsWith("@demo.invalid");
        assertThat(portfolioRepository.findByUserId(demo.getId())).hasSize(4);
        assertThat(transactionRepository.findAllForValuation(demo.getId(), null, null)).hasSizeGreaterThan(36);

        assertThat(demoService.purgeExpired()).isZero(); // moins de 24 h
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", LocalDateTime.now().minusHours(25), demo.getId());
        assertThat(demoService.purgeExpired()).isEqualTo(1);
        assertThat(userRepository.findById(demo.getId())).isEmpty();
        assertThat(portfolioRepository.findByUserId(demo.getId())).isEmpty();
    }
}

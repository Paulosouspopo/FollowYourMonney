package com.portfolio.tracker.analysis;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.marketdata.AssetProfile;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WLD.PA (ETF monde) passe de 100 € à 110 € il y a 10 jours ; ACT.PA (action française) reste à 50 €. */
@DisplayName("Radiographie et contributions")
class AnalysisFlowTest extends AbstractIntegrationTest {

    @Autowired private ExposureService exposureService;
    @Autowired private ContributionService contributionService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("ana-" + UUID.randomUUID() + "@fym.io")
                .username("ana-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("WLD.PA")).thenReturn(Optional.of(new MarketQuote("WLD.PA", new BigDecimal("110"),
                "EUR", LocalDateTime.now(), today, "Amundi MSCI World", "Paris", "ETF")));
        when(marketDataProvider.getQuote("ACT.PA")).thenReturn(Optional.of(new MarketQuote("ACT.PA", new BigDecimal("50"),
                "EUR", LocalDateTime.now(), today, "Action Française", "Paris", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            String symbol = inv.getArgument(0);
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                BigDecimal price = symbol.equals("ACT.PA") ? new BigDecimal("50")
                        : d.isBefore(today.minusDays(10)) ? new BigDecimal("100") : new BigDecimal("110");
                points.add(new MarketPricePoint(symbol, price, "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
        when(marketDataProvider.getProfile("WLD.PA")).thenReturn(Optional.of(new AssetProfile("ETF",
                "Amundi MSCI World UCITS ETF", null, null, Map.of("technology", 0.6, "healthcare", 0.4), List.of(),
                1.0, 0.0, 0.0, 0.0, 0.38)));
        when(marketDataProvider.getProfile("ACT.PA")).thenReturn(Optional.of(new AssetProfile("EQUITY", "Action Française",
                "France", "Industrials", Map.of(), List.of(), null, null, null, null, null)));
    }

    @Test
    @DisplayName("Exposition calculée depuis les profils mis en cache ; frais saisis prioritaires ; contribution par ligne")
    void analysis() {
        UUID pea = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
        transactionService.create(pea, new TransactionCreateRequest("WLD.PA", TransactionType.BUY, BigDecimal.TEN,
                new BigDecimal("100"), null, "EUR", today.minusDays(60).atTime(10, 0), null), user.getId());
        transactionService.create(pea, new TransactionCreateRequest("ACT.PA", TransactionType.BUY, new BigDecimal("20"),
                new BigDecimal("50"), null, "EUR", today.minusDays(60).atTime(10, 0), null), user.getId());

        ExposureCalculator.Exposure e = exposureService.exposure(user.getId(), null);
        assertThat(e.totalEur()).isEqualTo(2100); // 10 × 110 + 20 × 50
        assertThat(e.countries().stream().filter(s -> s.key().equals("FR")).findFirst().orElseThrow().valueEur())
                .isEqualTo(1029.7); // 1000 en direct + 2,7 % de 1100
        assertThat(e.sectors()).extracting(ExposureCalculator.Slice::key).contains("technology", "industrials");
        assertThat(e.fees().weightedTerPct()).isEqualTo(0.38);

        exposureService.setAnnualFee(user.getId(), "WLD.PA", new BigDecimal("0.2"));
        ExposureCalculator.Exposure again = exposureService.exposure(user.getId(), null);
        assertThat(again.fees().lines()).singleElement().satisfies(f -> {
            assertThat(f.terPct()).isEqualTo(0.2);
            assertThat(f.userProvided()).isTrue();
        });
        verify(marketDataProvider, times(1)).getProfile("WLD.PA"); // en cache au deuxième appel

        ContributionService.Report month = contributionService.contributions(user.getId(), null, "1m");
        assertThat(month.lines()).first().satisfies(l -> {
            assertThat(l.symbol()).isEqualTo("WLD.PA");
            assertThat(l.gainEur()).isEqualTo(100); // 1000 → 1100 sur le mois
            assertThat(l.returnPct()).isEqualTo(10);
        });
        assertThat(month.gainEur()).isEqualTo(100);
        ContributionService.Report all = contributionService.contributions(user.getId(), null, "all");
        assertThat(all.lines()).extracting(ContributionService.Line::gainEur).containsExactly(100.0, 0.0);
    }
}

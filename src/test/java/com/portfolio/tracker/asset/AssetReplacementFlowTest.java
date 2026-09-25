package com.portfolio.tracker.asset;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.quality.DataQualityService;
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
import org.springframework.transaction.support.TransactionTemplate;

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

/** RCE (New York, USD) et RCE.MI (Milan, EUR) : la même société, deux cotations. */
@DisplayName("Remplacer l'actif d'une ligne")
class AssetReplacementFlowTest extends AbstractIntegrationTest {

    @Autowired private AssetReplacementService service;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private DataQualityService qualityService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;

    private final LocalDate today = LocalDate.now();
    private User user;
    private UUID pea;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("repl-" + UUID.randomUUID() + "@fym.io")
                .username("repl-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("RCE")).thenReturn(Optional.of(quote("RCE", "USD", "NYSE")));
        when(marketDataProvider.getQuote("RCE.MI")).thenReturn(Optional.of(quote("RCE.MI", "EUR", "Milan")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("400"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
        pea = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
    }

    @Test
    @DisplayName("Mauvaise cotation dans un PEA : l'actif est remplacé, les opérations conservées, l'alerte PEA disparaît")
    void remplacement() {
        buy("RCE", "2", 5);
        UUID assetId = assetId("RCE");
        assertThat(qualityService.audit(user.getId())).extracting(i -> i.code()).contains("PEA_INELIGIBLE");

        service.replace(pea, assetId, "rce.mi", user.getId());

        Asset asset = tx.execute(s -> assetRepository.findById(assetId).orElseThrow());
        assertThat(asset.getSymbol()).isEqualTo("RCE.MI");
        assertThat(asset.getCurrency()).isEqualTo("EUR");
        assertThat(transactionRepository.findByAssetIdAndUserId(assetId, user.getId())).hasSize(1);
        assertThat(qualityService.audit(user.getId())).extracting(i -> i.code()).doesNotContain("PEA_INELIGIBLE");
    }

    @Test
    @DisplayName("Nouvel actif déjà présent : les deux lignes sont fusionnées")
    void fusion() {
        buy("RCE", "2", 5);
        buy("RCE.MI", "3", 4);
        service.replace(pea, assetId("RCE"), "RCE.MI", user.getId());

        List<String> symbols = tx.execute(s -> assetRepository.findByPortfolioIdAndUserId(pea, user.getId())
                .stream().map(Asset::getSymbol).toList());
        assertThat(symbols).containsExactly("RCE.MI");
        assertThat(transactionRepository.findByAssetIdAndUserId(assetId("RCE.MI"), user.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Fusion refusée si une vente dépassait alors la quantité détenue ; actif inconnu refusé")
    void refus() {
        buy("RCE", "2", 10);
        buy("RCE.MI", "1", 6);
        transactionService.create(pea, new TransactionCreateRequest("RCE.MI", TransactionType.SELL, BigDecimal.ONE,
                new BigDecimal("400"), null, "EUR", today.minusDays(5).atTime(10, 0), null), user.getId());
        // Fusion : l'achat RCE (j-10) précède tout, la vente reste couverte → acceptée
        service.replace(pea, assetId("RCE"), "RCE.MI", user.getId());

        UUID other = portfolioRepository.save(Portfolio.builder().name("CTO").type(PortfolioType.CTO).user(user).build()).getId();
        transactionService.create(other, new TransactionCreateRequest("RCE.MI", TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal("400"), null, "EUR", today.minusDays(3).atTime(10, 0), null), user.getId());
        transactionService.create(other, new TransactionCreateRequest("RCE.MI", TransactionType.SELL, BigDecimal.ONE,
                new BigDecimal("400"), null, "EUR", today.minusDays(2).atTime(10, 0), null), user.getId());
        transactionService.create(other, new TransactionCreateRequest("RCE", TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal("400"), null, "EUR", today.minusDays(1).atTime(10, 0), null), user.getId());
        UUID rce = tx.execute(s -> assetRepository.findBySymbolAndPortfolioIdAndUserId("RCE", other, user.getId())
                .orElseThrow().getId());
        transactionService.create(other, new TransactionCreateRequest("RCE", TransactionType.SELL, BigDecimal.ONE,
                new BigDecimal("400"), null, "EUR", today.atStartOfDay(), null), user.getId());
        // Deux lignes soldées : la fusion reste cohérente (jamais négative)
        service.replace(other, rce, "RCE.MI", user.getId());

        assertThatThrownBy(() -> service.replace(pea, assetId("RCE.MI"), "INCONNU", user.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    private MarketQuote quote(String symbol, String currency, String exchange) {
        return new MarketQuote(symbol, new BigDecimal("400"), currency, LocalDateTime.now(), today, "Ferrari N.V.", exchange, "EQUITY");
    }

    private void buy(String symbol, String quantity, int daysAgo) {
        transactionService.create(pea, new TransactionCreateRequest(symbol, TransactionType.BUY, new BigDecimal(quantity),
                new BigDecimal("400"), null, "EUR", today.minusDays(daysAgo).atTime(10, 0), null), user.getId());
    }

    private UUID assetId(String symbol) {
        return tx.execute(s -> assetRepository.findBySymbolAndPortfolioIdAndUserId(symbol, pea, user.getId()).orElseThrow().getId());
    }
}

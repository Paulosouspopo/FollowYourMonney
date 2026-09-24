package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.assetprice.PriceHistoryCoverageRepository;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.exchangerate.ExchangeRateRepository;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.transaction.dto.TransactionUpdateRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flux complet : transaction passée → historique de prix → snapshots → courbe,
 * via les vrais services (seul le provider Yahoo est simulé).
 */
@DisplayName("Historique — flux transaction → prix → snapshots")
class PortfolioHistoryFlowTest extends AbstractIntegrationTest {

    private static final String AAPL = "AAPL";
    private static final String USD_EUR = "USDEUR=X";

    /** Cours simulés : constants dans le temps pour des assertions lisibles. */
    private static final Map<String, BigDecimal> CLOSES = Map.of(
            AAPL, new BigDecimal("150"),
            USD_EUR, new BigDecimal("0.9"));

    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioValuationService valuationService;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;
    @Autowired private AssetPriceRepository assetPriceRepository;
    @Autowired private PriceHistoryCoverageRepository coverageRepository;
    @Autowired private ExchangeRateRepository exchangeRateRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private UUID userId;
    private UUID portfolioId;

    @BeforeEach
    void seed() {
        snapshotRepository.deleteAll();
        assetPriceRepository.deleteAll();
        coverageRepository.deleteAll();
        exchangeRateRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .email("flow-" + UUID.randomUUID() + "@fym.io")
                .username("flow-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .build());
        userId = user.getId();
        portfolioId = portfolioRepository.save(
                Portfolio.builder().name("CTO").type(PortfolioType.CTO).user(user).build()).getId();

        when(marketDataProvider.getQuote(AAPL)).thenReturn(Optional.of(quote(AAPL, "150", "USD", "EQUITY")));
        when(marketDataProvider.getQuote(USD_EUR)).thenReturn(Optional.of(quote(USD_EUR, "0.92", "EUR", "CURRENCY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            String symbol = inv.getArgument(0);
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(symbol, CLOSES.get(symbol),
                        symbol.equals(AAPL) ? "USD" : "EUR", d, d.atTime(15, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Achat passé en USD : taux du jour d'achat, courbe complète, point du jour = dashboard")
    void achatPasseEnDevise() {
        TransactionResponse buy = transactionService.create(portfolioId, buy("10", "100", 10), userId);

        // Taux historique du jour de l'achat (0.9), pas le taux courant (0.92)
        assertThat(buy.exchangeRateToEur()).isEqualByComparingTo("0.9");

        List<PortfolioSnapshot> curve = snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
        assertThat(curve).hasSize(11);
        assertThat(curve.get(0).getSnapshotDate()).isEqualTo(today.minusDays(10));

        // J-5 : 10 × 150 USD × 0.9 (taux de J-5), investi 10 × 100 × 0.9
        PortfolioSnapshot j5 = at(curve, today.minusDays(5));
        assertThat(j5.getTotalValue()).isEqualByComparingTo("1350.00");
        assertThat(j5.getTotalInvested()).isEqualByComparingTo("900.00");
        assertThat(j5.getGainLoss()).isEqualByComparingTo("450.00");

        // Aujourd'hui : taux courant, identique à la valorisation live du dashboard
        PortfolioSnapshot todaySnapshot = at(curve, today);
        assertThat(todaySnapshot.getTotalValue()).isEqualByComparingTo("1380.00");
        assertThat(valuationService.valuate(userId, portfolioId, null).getTotalValueEur())
                .isEqualByComparingTo(todaySnapshot.getTotalValue());
    }

    @Test
    @DisplayName("Deuxième transaction sur un symbole déjà connu : snapshots recalculés sans re-télécharger")
    void deuxiemeTransactionMemeSymbole() {
        transactionService.create(portfolioId, buy("10", "100", 10), userId);
        transactionService.create(portfolioId, new TransactionCreateRequest(portfolioId, AAPL,
                TransactionType.SELL, new BigDecimal("5"), new BigDecimal("160"), null, "USD",
                today.minusDays(3).atTime(12, 0), null), userId);

        List<PortfolioSnapshot> curve = snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
        assertThat(at(curve, today.minusDays(4)).getTotalValue()).isEqualByComparingTo("1350.00");
        assertThat(at(curve, today.minusDays(3)).getTotalValue()).isEqualByComparingTo("675.00");
        assertThat(at(curve, today.minusDays(3)).getTotalInvested()).isEqualByComparingTo("450.00");

        // L'historique n'a été demandé qu'une fois par symbole
        verify(marketDataProvider, times(1)).getDailyHistory(eq(AAPL), any(), any());
        verify(marketDataProvider, times(1)).getDailyHistory(eq(USD_EUR), any(), any());
    }

    @Test
    @DisplayName("Transaction déplacée plus tôt : seul le trou est téléchargé, la courbe commence à la nouvelle date")
    void transactionDeplacee() {
        TransactionResponse buy = transactionService.create(portfolioId, buy("10", "100", 10), userId);

        transactionService.update(buy.id(), new TransactionUpdateRequest(TransactionType.BUY,
                new BigDecimal("10"), new BigDecimal("100"), null,
                today.minusDays(12).atTime(12, 0), null), userId);

        verify(marketDataProvider).getDailyHistory(AAPL, today.minusDays(12), today.minusDays(11));

        List<PortfolioSnapshot> curve = snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
        assertThat(curve).hasSize(13);
        assertThat(curve.get(0).getSnapshotDate()).isEqualTo(today.minusDays(12));
        assertThat(curve).allSatisfy(s -> assertThat(s.getTotalValue()).isPositive());
    }

    @Test
    @DisplayName("Suppression de la dernière transaction : plus aucun snapshot")
    void suppression() {
        TransactionResponse buy = transactionService.create(portfolioId, buy("10", "100", 10), userId);
        assertThat(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId)).isNotEmpty();

        transactionService.deleteById(buy.id(), userId);

        assertThat(snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId)).isEmpty();
    }

    @Test
    @DisplayName("Prix : une seule ligne par symbole et par jour, même après plusieurs mises à jour")
    void unPrixParJour() {
        transactionService.create(portfolioId, buy("10", "100", 10), userId);
        transactionService.create(portfolioId, buy("1", "100", 2), userId);

        assertThat(assetPriceRepository.findBySymbolAndPriceDateBetween(AAPL, today.minusDays(10), today))
                .hasSize(11);
    }

    // ------------------------------------------------------------------ utils

    private TransactionCreateRequest buy(String qty, String price, int daysAgo) {
        return new TransactionCreateRequest(portfolioId, AAPL, TransactionType.BUY,
                new BigDecimal(qty), new BigDecimal(price), null, "USD",
                today.minusDays(daysAgo).atTime(12, 0), null);
    }

    private MarketQuote quote(String symbol, String price, String currency, String type) {
        return new MarketQuote(symbol, new BigDecimal(price), currency, LocalDateTime.now(), today,
                symbol, "NMS", type);
    }

    private static PortfolioSnapshot at(List<PortfolioSnapshot> curve, LocalDate day) {
        return curve.stream().filter(s -> s.getSnapshotDate().equals(day)).findFirst()
                .orElseThrow(() -> new AssertionError("Pas de snapshot au " + day));
    }
}

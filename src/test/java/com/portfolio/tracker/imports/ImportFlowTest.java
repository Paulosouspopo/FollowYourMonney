package com.portfolio.tracker.imports;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.imports.dto.*;
import com.portfolio.tracker.imports.dto.AssetResolutionDto.Confidence;
import com.portfolio.tracker.marketdata.AssetSearchResult;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Parcours d'import complet (aperçu → validation → réimport), Yahoo simulé. */
@DisplayName("Import de relevés — parcours complet")
class ImportFlowTest extends AbstractIntegrationTest {

    @Autowired private ImportService importService;
    @Autowired private PortfolioValuationService valuationService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .email("import-" + UUID.randomUUID() + "@fym.io")
                .username("import-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .emailVerified(true)
                .build());
        userId = user.getId();

        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.search("US0000000001")).thenReturn(List.of(
                new AssetSearchResult("FAKE", "Fake Corp", "NYSE", AssetType.ACTION)));
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        stubQuote("FAKE", "105", "EQUITY");
        stubQuote("BTC-EUR", "60000", "CRYPTOCURRENCY");
        stubQuote("SOL-EUR", "120", "CRYPTOCURRENCY");
        stubQuote("USDT-EUR", "0.95", "CRYPTOCURRENCY");
        stubQuote("ETH-EUR", "3000", "CRYPTOCURRENCY");
        // Historique : USDT à 0,95 € chaque jour (sert à estimer la conversion USDT → ETH)
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            String symbol = inv.getArgument(0);
            if (!symbol.equals("USDT-EUR")) {
                return List.of();
            }
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(symbol, new BigDecimal("0.95"), "EUR", d, d.atTime(15, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Trade Republic : aperçu, validation avec activation des liquidités, réimport sans doublon")
    void tradeRepublic() throws IOException {
        UUID portfolioId = portfolio("CTO", PortfolioType.CTO);
        byte[] file = fixture("trade-republic.csv");

        ImportPreview preview = importService.preview(userId, new PreviewOptions(portfolioId, null, null), file);
        assertThat(preview.format()).isEqualTo(ImportFormat.TRADE_REPUBLIC);
        assertThat(preview.cashTrackingEnabled()).isFalse();
        Map<String, AssetResolutionDto> assets = preview.assets().stream()
                .collect(Collectors.toMap(AssetResolutionDto::reference, a -> a));
        assertThat(assets.get("ISIN:US0000000001").confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(assets.get("ISIN:US0000000001").suggestion().symbol()).isEqualTo("FAKE");
        assertThat(assets.get("CRYPTO:BTC").suggestion().symbol()).isEqualTo("BTC-EUR");

        ImportCommitResult result = importService.commit(userId, commitAll(portfolioId, preview, true));
        assertThat(result.transactions()).isEqualTo(4);
        assertThat(result.cashMovements()).isEqualTo(3);

        PortfolioValuation valuation = valuationService.valuate(userId, portfolioId, null).getPortfolios().get(0);
        assertThat(valuation.isCashTracking()).isTrue();
        // 1000 - (200 + 1,60) - (50 + 1) + (1,65 - 0,22) + (110 - 1) - 50 + 5
        assertThat(valuation.getCashEur()).isEqualByComparingTo("812.83");
        assertThat(valuation.getDividendsEur()).isEqualByComparingTo("1.65");

        // Réimport du même relevé : tout est reconnu comme déjà présent
        ImportPreview again = importService.preview(userId, new PreviewOptions(portfolioId, null, null), file);
        assertThat(again.rows()).noneMatch(r -> r.status() == RowStatus.READY);
        assertThat(again.assets()).allMatch(a -> a.confidence() == Confidence.REMEMBERED);
        ImportCommitResult replay = importService.commit(userId, commitAll(portfolioId, preview, true));
        assertThat(replay.skipped()).isEqualTo(7);
        assertThat(transactionRepository.findByPortfolioIdAndUserId(portfolioId, userId)).hasSize(4);
    }

    @Test
    @DisplayName("Binance : jambes appariées, conversion USDT → ETH valorisée au cours du jour")
    void binance() throws IOException {
        UUID portfolioId = portfolio("Binance", PortfolioType.CRYPTO);
        ImportPreview preview = importService.preview(userId, new PreviewOptions(portfolioId, null, null),
                fixture("binance.csv"));
        assertThat(preview.format()).isEqualTo(ImportFormat.BINANCE);

        ImportRowDto ethBuy = preview.rows().stream()
                .filter(r -> r.priceEstimated() && r.kind() == ImportKind.BUY).findFirst().orElseThrow();
        // 309.50019 USDT × 0,95 € / 0,1 ETH
        assertThat(ethBuy.unitPrice()).isEqualByComparingTo("2940.25180500");

        ImportCommitResult result = importService.commit(userId, commitAll(portfolioId, preview, true));
        assertThat(result.transactions()).isEqualTo(6);
        assertThat(result.cashMovements()).isEqualTo(1);

        PortfolioValuation valuation = valuationService.valuate(userId, portfolioId, null).getPortfolios().get(0);
        // 500 versés - 100 (BTC) - 50 (SOL) - 300 (USDT) + 60 (vente BTC) ; la conversion est neutre
        assertThat(valuation.getCashEur()).isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("Une ligne invalide annule tout l'import et est désignée")
    void ligneInvalide() throws IOException {
        UUID portfolioId = portfolio("CTO", PortfolioType.CTO);
        ImportPreview preview = importService.preview(userId, new PreviewOptions(portfolioId, null, null),
                fixture("trade-republic.csv"));
        // Sans l'achat : la vente dépasse la quantité détenue
        List<ImportRowDto> rows = preview.rows().stream()
                .filter(r -> r.status() == RowStatus.READY)
                .filter(r -> !(r.kind() == ImportKind.BUY && "ISIN:US0000000001".equals(r.assetReference())))
                .toList();
        ImportRowDto sell = rows.stream().filter(r -> r.kind() == ImportKind.SELL).findFirst().orElseThrow();

        assertThatThrownBy(() -> importService.commit(userId,
                new ImportCommitRequest(portfolioId, true, suggestions(preview), rows)))
                .isInstanceOf(ImportRowException.class)
                .satisfies(e -> assertThat(((ImportRowException) e).getRowId()).isEqualTo(sell.id()))
                .hasMessageContaining("Vente");

        assertThat(transactionRepository.findByPortfolioIdAndUserId(portfolioId, userId)).isEmpty();
        assertThat(portfolioRepository.findById(portfolioId).orElseThrow().isCashTracking()).isFalse();
    }

    @Test
    @DisplayName("Livret : les opérations sur actifs sont en erreur dans l'aperçu")
    void livret() throws IOException {
        UUID livret = portfolio("Livret A", PortfolioType.LIVRET);
        ImportPreview preview = importService.preview(userId, new PreviewOptions(livret, null, null),
                fixture("trade-republic.csv"));
        assertThat(preview.rows()).filteredOn(r -> r.kind() != null && r.kind().isTrade() && r.status() != RowStatus.IGNORED)
                .allMatch(r -> r.status() == RowStatus.ERROR);
    }

    // ------------------------------------------------------------------ utils

    private ImportCommitRequest commitAll(UUID portfolioId, ImportPreview preview, boolean enableCash) {
        List<ImportRowDto> ready = preview.rows().stream().filter(r -> r.status() == RowStatus.READY).toList();
        return new ImportCommitRequest(portfolioId, enableCash, suggestions(preview), ready);
    }

    private static Map<String, String> suggestions(ImportPreview preview) {
        return preview.assets().stream().filter(a -> a.suggestion() != null)
                .collect(Collectors.toMap(AssetResolutionDto::reference, a -> a.suggestion().symbol()));
    }

    private UUID portfolio(String name, PortfolioType type) {
        return portfolioRepository.save(Portfolio.builder().name(name).type(type).user(user)
                .cashTracking(type == PortfolioType.LIVRET).build()).getId();
    }

    private void stubQuote(String symbol, String price, String type) {
        when(marketDataProvider.getQuote(symbol)).thenReturn(Optional.of(new MarketQuote(symbol, new BigDecimal(price),
                "EUR", LocalDateTime.now(), LocalDate.now(), symbol + " name", "TEST", type)));
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = ImportFlowTest.class.getResourceAsStream("/imports/" + name)) {
            return in.readAllBytes();
        }
    }
}

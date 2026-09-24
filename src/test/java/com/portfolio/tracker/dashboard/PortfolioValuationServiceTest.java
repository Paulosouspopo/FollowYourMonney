package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.assetprice.dto.LatestPriceProjection;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioValuationService — moteur de valorisation")
class PortfolioValuationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AssetPriceRepository assetPriceRepository;

    @Mock
    private CurrencyConverter currencyConverter;

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private PortfolioValuationService valuationService;

    private CurrencyConverter.Session mockSession;

    private UUID userId;
    private Portfolio portfolio;
    private Asset btc;

    @BeforeEach
    void setUp() {

        mockSession = mock(CurrencyConverter.Session.class);
        lenient()
                .when(currencyConverter.openSession()).thenReturn(mockSession);
        lenient()
                .when(mockSession.toEur(any(BigDecimal.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient()
                .when(assetPriceRepository.findLatestForSymbols(any())).thenReturn(List.of());

        lenient()
                .when(assetPriceRepository.findLatestForSymbolsAsOf(any(), any())).thenReturn(List.of());

        userId = UUID.randomUUID();

        portfolio = Portfolio.builder()
                .id(UUID.randomUUID())
                .name("PEA Principal")
                .type(PortfolioType.PEA)
                .build();

        btc = Asset.builder()
                .id(UUID.randomUUID())
                .symbol("BTC-USD")
                .name("Bitcoin")
                .assetType(AssetType.CRYPTO)
                .portfolio(portfolio)
                .build();

        lenient().when(portfolioRepository.findByUserId(userId)).thenReturn(List.of(portfolio));
        lenient().when(portfolioRepository.findByIdAndUserId(portfolio.getId(), userId))
                .thenReturn(Optional.of(portfolio));
    }

    // ---------------- Helpers ----------------

    private Transaction tx(TransactionType type,
            String quantity,
            String pricePerUnit,
            String fees,
            int daysAgo) {
        return tx(btc, type, quantity, pricePerUnit, fees, daysAgo);
    }

    private Transaction tx(Asset asset,
            TransactionType type,
            String quantity,
            String pricePerUnit,
            String fees,
            int daysAgo) {

        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal price = new BigDecimal(pricePerUnit);
        BigDecimal feesAmount = new BigDecimal(fees);
        BigDecimal totalAmount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal exchangeRate = BigDecimal.ONE; // devise = EUR dans ces tests

        return Transaction.builder()
                .asset(asset)
                .type(type)
                .quantity(qty)
                .pricePerUnit(price)
                .fees(feesAmount)
                .totalAmount(totalAmount)
                .currency("EUR")
                .exchangeRateToEur(exchangeRate)
                .totalAmountEur(totalAmount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
                .feesEur(feesAmount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP))
                .transactionDate(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    private void givenTransactions(Transaction... txs) {
        when(transactionRepository.findAllForValuation(any(), any(), any()))
                .thenReturn(List.of(txs));
    }

    /**
     * Stub du prix courant.
     * Chaque élément de `rows` doit être un Object[4] : {symbol, price, currency,
     * lastUpdated}.
     */
    private void givenPrices(Object[]... rows) {
        List<LatestPriceProjection> projections = new ArrayList<>();

        for (Object[] fields : rows) {
            LatestPriceProjection p = mock(LatestPriceProjection.class);

            lenient().when(p.getSymbol()).thenReturn((String) fields[0]);
            lenient().when(p.getPrice()).thenReturn((BigDecimal) fields[1]);
            lenient().when(p.getCurrency()).thenReturn((String) fields[2]);
            lenient().when(p.getLastUpdated()).thenReturn((LocalDateTime) fields[3]);

            projections.add(p);
        }

        lenient().when(assetPriceRepository.findLatestForSymbols(anyList()))
                .thenReturn(projections);
        lenient().when(assetPriceRepository.findLatestForSymbolsAsOf(anyList(), any(LocalDateTime.class)))
                .thenReturn(projections);
    }

    private void givenPrice(String symbol, String price) {
        givenPrices(new Object[] { symbol, new BigDecimal(price), "EUR", LocalDateTime.now() });
    }

    private void givenNoPrice() {
        lenient().when(assetPriceRepository.findLatestForSymbols(anyList()))
                .thenReturn(List.of());
        lenient().when(assetPriceRepository.findLatestForSymbolsAsOf(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of());
    }

    private PositionValuation firstPosition(ValuationResult result) {
        return result.getPortfolios().get(0).getPositions().get(0);
    }

    // ---------------- Scénarios ----------------

    @Nested
    @DisplayName("Achat simple")
    class AchatSimple {

        @Test
        @DisplayName("1 BTC acheté 30 000 €, cours 40 000 € → +10 000 € soit +33,33 %")
        void achatSimple() {
            givenTransactions(tx(TransactionType.BUY, "1", "30000", "0", 10));
            givenPrice("BTC-USD", "40000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("1");
            assertThat(pos.getInvestedEur()).isEqualByComparingTo("30000.00");
            assertThat(pos.getCurrentValueEur()).isEqualByComparingTo("40000.00");
            assertThat(pos.getUnrealizedGainEur()).isEqualByComparingTo("10000.00");
            assertThat(pos.getUnrealizedGainPercentage()).isEqualByComparingTo("33.33");
            assertThat(pos.isPriceMissing()).isFalse();
        }

        @Test
        @DisplayName("Les frais sont intégrés au prix de revient")
        void fraisDansLeCoutDeRevient() {
            givenTransactions(tx(TransactionType.BUY, "1", "30000", "50", 10));
            givenPrice("BTC-USD", "30000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getInvestedEur()).isEqualByComparingTo("30050.00");
            assertThat(pos.getUnrealizedGainEur()).isEqualByComparingTo("-50.00");
        }
    }

    @Nested
    @DisplayName("CUMP — achats multiples à prix différents")
    class Cump {

        @Test
        @DisplayName("1 BTC à 20 000 € puis 1 BTC à 40 000 € → CUMP 30 000 €")
        void cumpDeuxAchats() {
            givenTransactions(
                    tx(TransactionType.BUY, "1", "20000", "0", 20),
                    tx(TransactionType.BUY, "1", "40000", "0", 10));
            givenPrice("BTC-USD", "35000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("2");
            assertThat(pos.getInvestedEur()).isEqualByComparingTo("60000.00");
            assertThat(pos.getAverageCostEur()).isEqualByComparingTo("30000.00");
            assertThat(pos.getCurrentValueEur()).isEqualByComparingTo("70000.00");
            assertThat(pos.getUnrealizedGainEur()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("Quantités fractionnaires — CUMP non arrondi prématurément")
        void cumpFractionnaire() {
            givenTransactions(
                    tx(TransactionType.BUY, "0.3", "30000", "0", 20),
                    tx(TransactionType.BUY, "0.7", "20000", "0", 10));
            givenPrice("BTC-USD", "25000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            // (9 000 + 14 000) / 1 = 23 000
            assertThat(pos.getQuantity()).isEqualByComparingTo("1.0");
            assertThat(pos.getAverageCostEur()).isEqualByComparingTo("23000.00");
            assertThat(pos.getUnrealizedGainEur()).isEqualByComparingTo("2000.00");
        }
    }

    @Nested
    @DisplayName("Ventes")
    class Ventes {

        @Test
        @DisplayName("Vente partielle — CUMP inchangé, investi réduit au prorata")
        void ventePartielle() {
            givenTransactions(
                    tx(TransactionType.BUY, "2", "30000", "0", 20),
                    tx(TransactionType.SELL, "1", "40000", "0", 5));
            givenPrice("BTC-USD", "40000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("1");
            assertThat(pos.getAverageCostEur()).isEqualByComparingTo("30000.00");
            assertThat(pos.getInvestedEur()).isEqualByComparingTo("30000.00");
            assertThat(pos.getCurrentValueEur()).isEqualByComparingTo("40000.00");
            assertThat(pos.getRealizedGainEur()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("Vente totale — position soldée, plus-value entièrement réalisée")
        void venteTotale() {
            givenTransactions(
                    tx(TransactionType.BUY, "1", "30000", "0", 20),
                    tx(TransactionType.SELL, "1", "45000", "0", 5));
            givenPrice("BTC-USD", "50000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("0");
            assertThat(pos.getCurrentValueEur()).isEqualByComparingTo("0.00");
            assertThat(pos.getInvestedEur()).isEqualByComparingTo("0.00");
            assertThat(pos.getRealizedGainEur()).isEqualByComparingTo("15000.00");
            // La hausse survenue après la vente ne doit rien produire
            assertThat(pos.getUnrealizedGainEur()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Vente supérieure au stock détenu → plafonnée à 0, sans exception")
        void ventePlafonnee() {
            givenTransactions(
                    tx(TransactionType.BUY, "1", "30000", "0", 20),
                    tx(TransactionType.SELL, "5", "40000", "0", 5));
            givenPrice("BTC-USD", "40000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("0");
            assertThat(pos.getQuantity().signum()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Les frais de vente réduisent le produit net")
        void fraisDeVente() {
            givenTransactions(
                    tx(TransactionType.BUY, "1", "30000", "0", 20),
                    tx(TransactionType.SELL, "1", "40000", "100", 5));
            givenPrice("BTC-USD", "40000");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            // 40 000 − 100 de frais − 30 000 de coût = 9 900
            assertThat(pos.getRealizedGainEur()).isEqualByComparingTo("9900.00");
        }
    }

    @Nested
    @DisplayName("Dividendes")
    class Dividendes {

        @Test
        @DisplayName("Un dividende n'altère ni la quantité ni le CUMP")
        void dividendeNeutreSurLaPosition() {
            givenTransactions(
                    tx(TransactionType.BUY, "10", "100", "0", 20),
                    tx(TransactionType.DIVIDEND, "10", "5", "0", 5));
            givenPrice("BTC-USD", "100");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("10");
            assertThat(pos.getAverageCostEur()).isEqualByComparingTo("100.00");
            assertThat(pos.getInvestedEur()).isEqualByComparingTo("1000.00");
            assertThat(pos.getDividendsEur()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("Un dividende encaissé après la vente totale reste comptabilisé")
        void dividendeApresVente() {
            givenTransactions(
                    tx(TransactionType.BUY, "10", "100", "0", 30),
                    tx(TransactionType.SELL, "10", "120", "0", 10),
                    tx(TransactionType.DIVIDEND, "10", "2", "0", 5));
            givenPrice("BTC-USD", "130");

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("0");
            assertThat(pos.getDividendsEur()).isEqualByComparingTo("20.00");
            assertThat(pos.getRealizedGainEur()).isEqualByComparingTo("200.00");
        }
    }

    @Nested
    @DisplayName("Prix manquant")
    class PrixManquant {

        @Test
        @DisplayName("Aucun prix en base → position visible, valeur 0, flag priceMissing")
        void prixManquantNeCasseRien() {
            givenTransactions(tx(TransactionType.BUY, "1", "30000", "0", 10));
            givenNoPrice();

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.isPriceMissing()).isTrue();
            assertThat(pos.getQuantity()).isEqualByComparingTo("1");
            assertThat(pos.getInvestedEur()).isEqualByComparingTo("30000.00");
            assertThat(pos.getCurrentValueEur()).isEqualByComparingTo("0.00");
            assertThat(pos.getUnrealizedGainEur()).isEqualByComparingTo("-30000.00");
        }

        @Test
        @DisplayName("Position soldée sans prix → pas de flag, rien à valoriser")
        void positionSoldeeSansPrix() {
            givenTransactions(
                    tx(TransactionType.BUY, "1", "30000", "0", 20),
                    tx(TransactionType.SELL, "1", "35000", "0", 5));
            givenNoPrice();

            PositionValuation pos = firstPosition(valuationService.valuate(userId, null, null));

            assertThat(pos.getQuantity()).isEqualByComparingTo("0");
            assertThat(pos.isPriceMissing()).isFalse();
            assertThat(pos.getRealizedGainEur()).isEqualByComparingTo("5000.00");
        }
    }

    @Nested
    @DisplayName("Agrégation multi-portefeuilles")
    class Agregation {

        @Test
        @DisplayName("Deux portefeuilles → totaux cumulés et tri alphabétique")
        void deuxPortefeuilles() {
            Portfolio cto = Portfolio.builder()
                    .id(UUID.randomUUID())
                    .name("CTO Secondaire")
                    .type(PortfolioType.CTO)
                    .build();

            when(portfolioRepository.findByUserId(userId)).thenReturn(List.of(portfolio, cto));

            Asset eth = Asset.builder()
                    .id(UUID.randomUUID())
                    .symbol("ETH-USD")
                    .name("Ethereum")
                    .assetType(AssetType.CRYPTO)
                    .portfolio(cto)
                    .build();

            givenTransactions(
                    tx(TransactionType.BUY, "1", "30000", "0", 20),
                    tx(eth, TransactionType.BUY, "10", "1000", "0", 15));

            givenPrices(
                    new Object[] { "BTC-USD", new BigDecimal("40000"), "EUR", LocalDateTime.now() },
                    new Object[] { "ETH-USD", new BigDecimal("1500"), "EUR", LocalDateTime.now() });

            ValuationResult result = valuationService.valuate(userId, null, null);

            assertThat(result.getPortfolios()).hasSize(2);
            // Tri alphabétique : "CTO Secondaire" avant "PEA Principal"
            assertThat(result.getPortfolios())
                    .extracting(PortfolioValuation::getName)
                    .containsExactly("CTO Secondaire", "PEA Principal");

            assertThat(result.getTotalInvestedEur()).isEqualByComparingTo("40000.00");
            assertThat(result.getTotalValueEur()).isEqualByComparingTo("55000.00");
            assertThat(result.getTotalUnrealizedGainEur()).isEqualByComparingTo("15000.00");
            assertThat(result.getTotalUnrealizedGainPercentage()).isEqualByComparingTo("37.50");
        }

        @Test
        @DisplayName("Aucune transaction → portefeuille visible mais neutre, pas de division par zéro")
        void aucuneTransaction() {
            givenTransactions();

            ValuationResult result = valuationService.valuate(userId, null, null);

            assertThat(result.getPortfolios()).hasSize(1);
            assertThat(result.getPortfolios().get(0).getPositions()).isEmpty();
            assertThat(result.getTotalInvestedEur()).isEqualByComparingTo("0.00");
            assertThat(result.getTotalValueEur()).isEqualByComparingTo("0.00");
            assertThat(result.getTotalUnrealizedGainEur()).isEqualByComparingTo("0.00");
            assertThat(result.getTotalUnrealizedGainPercentage()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Investi nul (dividende seul) → pourcentage à 0, pas d'ArithmeticException")
        void pasDeDivisionParZero() {
            givenTransactions(tx(TransactionType.DIVIDEND, "10", "5", "0", 5));
            givenPrice("BTC-USD", "100");

            ValuationResult result = valuationService.valuate(userId, null, null);

            assertThat(result.getTotalUnrealizedGainPercentage()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Filtrage et bornes temporelles")
    class Filtrage {

        @Test
        @DisplayName("Le portfolioId est bien propagé au repository")
        void filtrageParPortefeuille() {
            UUID portfolioId = portfolio.getId();
            when(transactionRepository.findAllForValuation(userId, portfolioId, null))
                    .thenReturn(List.of(tx(TransactionType.BUY, "1", "30000", "0", 10)));
            givenPrice("BTC-USD", "40000");

            ValuationResult result = valuationService.valuate(userId, portfolioId, null);

            assertThat(result.getPortfolios()).hasSize(1);
            assertThat(result.getPortfolios().get(0).getName()).isEqualTo("PEA Principal");
        }

        @Test
        @DisplayName("La date asOf est bien propagée au repository (backfill)")
        void valorisationHistorique() {
            LocalDateTime asOf = LocalDateTime.now().minusDays(7);
            when(transactionRepository.findAllForValuation(
                    eq(userId),
                    isNull(),
                    argThat(d -> d != null && d.isEqual(asOf)))) // Compare par valeur, pas par référence
                    .thenReturn(List.of(tx(TransactionType.BUY, "1", "30000", "0", 20)));
            givenPrice("BTC-USD", "35000");

            ValuationResult result = valuationService.valuate(userId, null, asOf);

            assertThat(result.getTotalValueEur()).isEqualByComparingTo("35000.00");
        }
    }
}
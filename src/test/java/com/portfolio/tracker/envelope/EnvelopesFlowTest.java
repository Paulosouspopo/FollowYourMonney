package com.portfolio.tracker.envelope;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.asset.ManualAssets;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.manual.ManualAssetRequest;
import com.portfolio.tracker.asset.manual.ManualAssetService;
import com.portfolio.tracker.asset.manual.ValuationRequest;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.InterestEstimateService;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.PortfolioService;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.tax.TaxService;
import com.portfolio.tracker.tax.dto.TaxReport;
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
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assurance-vie (fonds euros + fonds non coté), PER, épargne salariale et
 * compte multidevise. Le dollar vaut 0,90 € tous les jours.
 */
@DisplayName("Enveloppes : assurance-vie, PER, épargne salariale, devises")
class EnvelopesFlowTest extends AbstractIntegrationTest {

    @Autowired private PortfolioService portfolioService;
    @Autowired private CashMovementService cashMovementService;
    @Autowired private TransactionService transactionService;
    @Autowired private ManualAssetService manualAssetService;
    @Autowired private InterestEstimateService interestEstimateService;
    @Autowired private PortfolioValuationService valuationService;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;
    @Autowired private TaxService taxService;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("env-" + UUID.randomUUID() + "@fym.io")
                .username("env-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("USDEUR=X")).thenReturn(Optional.of(new MarketQuote("USDEUR=X",
                new BigDecimal("0.9"), "EUR", LocalDateTime.now(), today, "USD/EUR", "CCY", "CURRENCY")));
        when(marketDataProvider.getQuote("AAPL")).thenReturn(Optional.of(new MarketQuote("AAPL",
                new BigDecimal("200"), "USD", LocalDateTime.now(), today, "Apple", "NASDAQ", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            String symbol = inv.getArgument(0);
            BigDecimal price = symbol.equals("USDEUR=X") ? new BigDecimal("0.9") : new BigDecimal("200");
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(symbol, price, symbol.equals("USDEUR=X") ? "EUR" : "USD", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Assurance-vie : fonds euros = solde, fonds non coté valorisé à la valeur saisie, jamais demandé à Yahoo")
    void lifeInsurance() {
        UUID av = create("Assurance-vie", PortfolioType.ASSURANCE_VIE, null, new BigDecimal("2.5"));
        cashMovementService.create(av, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("1000"),
                today.minusDays(40), null), user.getId());

        AssetResponse fund = manualAssetService.create(av, new ManualAssetRequest("Fonds Maison Actions", AssetType.FONDS, null),
                user.getId());
        assertThat(ManualAssets.isManual(fund.symbol())).isTrue();
        assertThat(fund.manual()).isTrue();
        transactionService.create(av, new TransactionCreateRequest(fund.symbol(), TransactionType.BUY, new BigDecimal("5"),
                new BigDecimal("100"), null, null, today.minusDays(30).atTime(10, 0), null), user.getId());
        manualAssetService.saveValuation(av, fund.id(), new ValuationRequest(today.minusDays(5), new BigDecimal("110")),
                user.getId());

        PortfolioValuation v = valuation(av);
        assertThat(v.isCashTracking()).isTrue(); // forcé pour une enveloppe
        assertThat(v.getCashEur()).isEqualByComparingTo("500"); // fonds euros
        assertThat(v.getCurrentValueEur()).isEqualByComparingTo("1050"); // 500 + 5 × 110
        assertThat(v.getPositions()).singleElement().satisfies(p -> assertThat(p.isPriceMissing()).isFalse());
        // La courbe donne le même chiffre aujourd'hui
        assertThat(snapshotRepository.findAll().stream().filter(s -> s.getPortfolio().getId().equals(av)
                && s.getSnapshotDate().equals(today)).findFirst().orElseThrow().getTotalValue()).isEqualByComparingTo("1050");
        verify(marketDataProvider, never()).getDailyHistory(startsWith(ManualAssets.PREFIX), any(), any());

        // Intérêts courus : 1000 € puis 500 € au fonds euros, à 2,5 %
        InterestEstimateService.InterestEstimate estimate = interestEstimateService.estimate(av, today.getYear(), user.getId());
        assertThat(estimate.method().name()).isEqualTo("DAILY");
        assertThat(estimate.fullYear()).isFalse();

        TaxReport tax = taxService.report(user.getId(), today.getYear());
        assertThat(tax.lifeInsurances()).singleElement().satisfies(l -> {
            assertThat(l.depositsEur()).isEqualByComparingTo("1000");
            assertThat(l.gainEur()).isEqualByComparingTo("50");
            assertThat(l.eightYearsDate()).isEqualTo(today.minusDays(40).plusYears(8));
        });
        // Une vente d'unités de compte n'est pas une cession de compte-titres
        assertThat(tax.securities().sales()).isEmpty();
    }

    @Test
    @DisplayName("PER : versements déductibles (case 6NS) et économie selon la tranche ; abondement réservé aux enveloppes salariales")
    void retirementAndEmployeeSavings() {
        UUID per = create("PER", PortfolioType.PER, null, null);
        cashMovementService.create(per, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("2000"),
                today.minusDays(1), null), user.getId());
        taxService.setMarginalTaxRate(user.getId(), 41);
        TaxReport tax = taxService.report(user.getId(), today.getYear());
        assertThat(tax.marginalTaxRate()).isEqualTo(41);
        assertThat(tax.retirementSavings().depositsEur()).isEqualByComparingTo("2000");
        assertThat(tax.retirementSavings().estimatedSavingEur()).isEqualByComparingTo("820");
        assertThat(tax.retirementSavings().boxes()).extracting(TaxReport.Box::code).containsExactly("6NS");
        assertThatThrownBy(() -> taxService.setMarginalTaxRate(user.getId(), 25)).isInstanceOf(BadRequestException.class);

        UUID pee = create("PEE", PortfolioType.EPARGNE_SALARIALE, null, null);
        cashMovementService.create(pee, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("600"),
                today.minusDays(10), null), user.getId());
        cashMovementService.create(pee, new CashMovementRequest(CashMovementType.ABONDEMENT, new BigDecimal("300"),
                today.minusDays(10), null), user.getId());
        PortfolioValuation v = valuation(pee);
        assertThat(v.getNetDepositsEur()).isEqualByComparingTo("900");
        assertThat(v.getEmployerContributionsEur()).isEqualByComparingTo("300");
        assertThat(taxService.report(user.getId(), today.getYear()).employeeSavings()).singleElement()
                .satisfies(e -> assertThat(e.employerContributionsEur()).isEqualByComparingTo("300"));

        UUID cto = create("CTO", PortfolioType.CTO, true, null);
        assertThatThrownBy(() -> cashMovementService.create(cto, new CashMovementRequest(CashMovementType.ABONDEMENT,
                BigDecimal.TEN, today, null), user.getId())).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Compte multidevise : solde en dollars valorisé au taux du jour ; refusé sur un compte en euros")
    void multiCurrency() {
        UUID euros = create("CTO €", PortfolioType.CTO, true, null);
        assertThatThrownBy(() -> cashMovementService.create(euros, new CashMovementRequest(CashMovementType.DEPOSIT,
                BigDecimal.TEN, today, null, "USD", null, null), user.getId())).isInstanceOf(BadRequestException.class);

        UUID ibkr = portfolioService.create(new PortfolioCreateRequest("IBKR", null, PortfolioType.CTO, true, null, null, true),
                user.getId()).id();
        cashMovementService.create(ibkr, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("1000"),
                today.minusDays(20), null), user.getId());
        cashMovementService.create(ibkr, new CashMovementRequest(CashMovementType.CONVERSION, new BigDecimal("900"),
                today.minusDays(19), null, "EUR", new BigDecimal("1000"), "USD"), user.getId());
        transactionService.create(ibkr, new TransactionCreateRequest("AAPL", TransactionType.BUY, new BigDecimal("2"),
                new BigDecimal("200"), BigDecimal.ONE, "USD", today.minusDays(10).atTime(16, 0), null), user.getId());

        PortfolioValuation v = valuation(ibkr);
        // 100 € + 599 $ (1000 - 400 - 1) × 0,90 = 639,10 € de liquidités ; 2 × 200 $ × 0,90 = 360 € de positions
        assertThat(v.getCashEur()).isEqualByComparingTo("639.10");
        assertThat(v.getCashBalances()).extracting(b -> b.currency() + "=" + b.amount().toPlainString())
                .containsExactly("EUR=100.00", "USD=599.00");
        assertThat(v.getCurrentValueEur()).isEqualByComparingTo("999.10");
        assertThat(v.getNetDepositsEur()).isEqualByComparingTo("1000");
    }

    private UUID create(String name, PortfolioType type, Boolean cash, BigDecimal rate) {
        return portfolioService.create(new PortfolioCreateRequest(name, null, type, cash, rate), user.getId()).id();
    }

    private PortfolioValuation valuation(UUID portfolioId) {
        return valuationService.valuate(user.getId(), portfolioId, null).getPortfolios().get(0);
    }
}

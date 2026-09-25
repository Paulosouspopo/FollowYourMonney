package com.portfolio.tracker.tax;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** TAXS.PA (action) et TAXC-EUR (crypto) cotent 100 € tous les jours. */
@DisplayName("Récapitulatif fiscal")
class TaxFlowTest extends AbstractIntegrationTest {

    @Autowired private TaxService taxService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private final int lastYear = today.getYear() - 1;
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("tax-" + UUID.randomUUID() + "@fym.io")
                .username("tax-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("TAXS.PA")).thenReturn(Optional.of(quote("TAXS.PA", "EQUITY")));
        when(marketDataProvider.getQuote("TAXC-EUR")).thenReturn(Optional.of(quote("TAXC-EUR", "CRYPTOCURRENCY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("CTO, crypto et PEA : plus-value, dividendes, 150 VH bis, cases, flat tax, 5 ans du PEA")
    void recapitulatif() {
        UUID cto = portfolio("CTO", PortfolioType.CTO, null);
        trade(cto, "TAXS.PA", TransactionType.BUY, "10", "50", LocalDate.of(lastYear, 2, 1));
        trade(cto, "TAXS.PA", TransactionType.SELL, "4", "100", LocalDate.of(lastYear, 6, 1)); // +200 €
        trade(cto, "TAXS.PA", TransactionType.DIVIDEND, "1", "30", LocalDate.of(lastYear, 7, 1));

        UUID wallet = portfolio("Wallet", PortfolioType.CRYPTO, null);
        trade(wallet, "TAXC-EUR", TransactionType.BUY, "10", "50", LocalDate.of(lastYear, 1, 10));
        trade(wallet, "TAXC-EUR", TransactionType.SELL, "5", "100", LocalDate.of(lastYear, 9, 1));

        UUID pea = portfolio("PEA", PortfolioType.PEA, today.minusYears(6));
        trade(pea, "TAXS.PA", TransactionType.BUY, "10", "80", today.minusDays(30));

        TaxReport r = taxService.report(user.getId(), null);
        assertThat(r.year()).isEqualTo(lastYear); // par défaut : l'année écoulée
        assertThat(r.years()).contains(lastYear, today.getYear());

        // Titres : PMP 50 € ; 4 vendus à 100 € → +200 € ; dividende 30 € ; flat tax 30 % × 230 €
        assertThat(r.securities().sales()).singleElement().satisfies(s -> assertThat(s.gainEur()).isEqualByComparingTo("200"));
        assertThat(r.securities().taxableGainEur()).isEqualByComparingTo("200");
        assertThat(r.securities().dividendsEur()).isEqualByComparingTo("30");
        assertThat(r.securities().estimatedTaxEur()).isEqualByComparingTo("69");
        assertThat(r.securities().boxes()).extracting(TaxReport.Box::code).containsExactly("3VG", "2DC");

        // Crypto : PTA 500 € ; V = 10 × 100 = 1000 € ; vente 500 € → part 250 € → +250 €
        assertThat(r.crypto().sales()).singleElement().satisfies(s -> {
            assertThat(s.portfolioValueEur()).isEqualByComparingTo("1000");
            assertThat(s.gainEur()).isEqualByComparingTo("250");
        });
        assertThat(r.crypto().exempt()).isFalse();
        assertThat(r.crypto().boxes()).extracting(TaxReport.Box::code).containsExactly("3AN");

        // PEA ouvert il y a 6 ans : 5 ans atteints ; versements estimés (sans suivi des liquidités)
        assertThat(r.peas()).singleElement().satisfies(p -> {
            assertThat(p.fiveYearsReached()).isTrue();
            assertThat(p.openedAtEstimated()).isFalse();
            assertThat(p.depositsEur()).isEqualByComparingTo("800");
            assertThat(p.depositsEstimated()).isTrue();
        });
    }

    @Test
    @DisplayName("Crypto : cessions de l'année ≤ 305 € → exonérées")
    void franchise() {
        UUID wallet = portfolio("Wallet", PortfolioType.CRYPTO, null);
        trade(wallet, "TAXC-EUR", TransactionType.BUY, "10", "50", LocalDate.of(lastYear, 1, 10));
        trade(wallet, "TAXC-EUR", TransactionType.SELL, "3", "100", LocalDate.of(lastYear, 9, 1)); // 300 €

        TaxReport r = taxService.report(user.getId(), lastYear);
        assertThat(r.crypto().exempt()).isTrue();
        assertThat(r.crypto().estimatedTaxEur()).isEqualByComparingTo("0");
        assertThat(r.crypto().boxes()).isEmpty();
    }

    private MarketQuote quote(String symbol, String type) {
        return new MarketQuote(symbol, new BigDecimal("100"), "EUR", LocalDateTime.now(), today, symbol, "Paris", type);
    }

    private UUID portfolio(String name, PortfolioType type, LocalDate openedAt) {
        return portfolioRepository.save(Portfolio.builder().name(name).type(type).user(user).openedAt(openedAt).build()).getId();
    }

    private void trade(UUID portfolioId, String symbol, TransactionType type, String qty, String price, LocalDate day) {
        transactionService.create(portfolioId, new TransactionCreateRequest(symbol, type, new BigDecimal(qty),
                new BigDecimal(price), null, "EUR", day.atTime(10, 0), null), user.getId());
    }
}

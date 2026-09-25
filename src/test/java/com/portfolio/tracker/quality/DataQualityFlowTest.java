package com.portfolio.tracker.quality;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.quality.dto.DataIssue;
import com.portfolio.tracker.quality.dto.DataWarning;
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

/** Yahoo simulé : QUAL.PA (action, Paris) et QCRY-EUR (crypto) cotent 100 € tous les jours. */
@DisplayName("Détection des erreurs de saisie")
class DataQualityFlowTest extends AbstractIntegrationTest {

    @Autowired private DataQualityService service;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    private final LocalDate today = LocalDate.now();
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder().email("qual-" + UUID.randomUUID() + "@fym.io")
                .username("qual-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("QUAL.PA")).thenReturn(Optional.of(quote("QUAL.PA", "EQUITY")));
        when(marketDataProvider.getQuote("QCRY-EUR")).thenReturn(Optional.of(quote("QCRY-EUR", "CRYPTOCURRENCY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Saisie : prix à 25 € pour un cours à 100 € → avertissement avec le cours proposé ; 105 € → rien")
    void saisie() {
        UUID cto = portfolio(PortfolioType.CTO);
        List<DataWarning> w = service.checkTransaction(user.getId(), cto, "QUAL.PA", TransactionType.BUY,
                today.minusDays(3), new BigDecimal("25"), "EUR");
        assertThat(w).singleElement().satisfies(x -> {
            assertThat(x.code()).isEqualTo("PRICE_MISMATCH");
            assertThat(x.suggestedPrice()).isEqualByComparingTo("100");
            assertThat(x.message()).contains("−75");
        });
        assertThat(service.checkTransaction(user.getId(), cto, "QUAL.PA", TransactionType.BUY,
                today.minusDays(3), new BigDecimal("105"), "EUR")).isEmpty();
    }

    @Test
    @DisplayName("PEA : une crypto est signalée dès la saisie")
    void peaSaisie() {
        UUID pea = portfolio(PortfolioType.PEA);
        assertThat(service.checkTransaction(user.getId(), pea, "QCRY-EUR", TransactionType.BUY,
                today.minusDays(2), new BigDecimal("100"), "EUR"))
                .extracting(DataWarning::code).containsExactly("PEA_INELIGIBLE");
    }

    @Test
    @DisplayName("Audit de l'existant : prix incohérent et crypto dans un PEA ; « c'est normal » les fait disparaître")
    void audit() {
        UUID pea = portfolio(PortfolioType.PEA);
        buy(pea, "QUAL.PA", "25");
        buy(pea, "QUAL.PA", "101"); // cohérent
        buy(pea, "QCRY-EUR", "100");

        List<DataIssue> issues = service.audit(user.getId());
        assertThat(issues).extracting(DataIssue::code).containsExactlyInAnyOrder("PRICE_MISMATCH", "PEA_INELIGIBLE");
        DataIssue price = issues.stream().filter(i -> i.code().equals("PRICE_MISMATCH")).findFirst().orElseThrow();
        assertThat(price.suggestedPrice()).isEqualByComparingTo("100");
        assertThat(price.transactionId()).isNotNull();

        issues.forEach(i -> service.dismiss(user.getId(), i.key()));
        assertThat(service.audit(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Écart énorme : division d'actions probable, une alerte pour des opérations semblables, une faute isolée à part")
    void division() {
        UUID cto = portfolio(PortfolioType.CTO);
        buy(cto, "QUAL.PA", "20000"); // ×200 : relevé d'avant une division
        buy(cto, "QUAL.PA", "19800"); // ×198 : même cause
        buy(cto, "QUAL.PA", "1000");  // ×10 : zéro en trop, cause différente

        List<DataIssue> issues = service.audit(user.getId());
        assertThat(issues).extracting(DataIssue::code).containsExactly("SPLIT_SUSPECTED", "SPLIT_SUSPECTED");
        assertThat(issues).allSatisfy(i -> assertThat(i.suggestedPrice()).isNull());
        assertThat(issues).anySatisfy(i -> assertThat(i.message()).contains("environ 200 fois"));

        assertThat(service.checkTransaction(user.getId(), cto, "QUAL.PA", TransactionType.BUY, today.minusDays(3),
                new BigDecimal("1000"), "EUR")).extracting(DataWarning::code).containsExactly("SPLIT_SUSPECTED");
    }

    private MarketQuote quote(String symbol, String type) {
        return new MarketQuote(symbol, new BigDecimal("100"), "EUR", LocalDateTime.now(), today, symbol, "Paris", type);
    }

    private UUID portfolio(PortfolioType type) {
        return portfolioRepository.save(Portfolio.builder().name(type.name()).type(type).user(user).build()).getId();
    }

    private void buy(UUID portfolioId, String symbol, String price) {
        transactionService.create(portfolioId, new TransactionCreateRequest(symbol, TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal(price), null, "EUR", today.minusDays(3).atTime(10, 0), null), user.getId());
    }
}

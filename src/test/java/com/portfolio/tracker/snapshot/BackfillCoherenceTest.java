package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.assetprice.AssetPrice;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Backfill — cohérence de la courbe d'historique")
class BackfillCoherenceTest extends AbstractIntegrationTest {

    @Autowired private PortfolioHistoryService historyService;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AssetPriceRepository assetPriceRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID portfolioId;

    @BeforeEach
    void seed() {
        snapshotRepository.deleteAll();
        assetPriceRepository.deleteAll();
        User user = userRepository.save(User.builder()
                .email("backfill-" + UUID.randomUUID() + "@fym.io")
                .username("test-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .build());
        userId = user.getId();

        Portfolio portfolio = portfolioRepository.save(
                Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build());
        portfolioId = portfolio.getId();

        Asset btc = assetRepository.save(Asset.builder()
                .symbol("BTC-USD").name("Bitcoin").portfolio(portfolio).build());

        // Achat il y a 30 jours
        transactionRepository.save(Transaction.builder()
                .asset(btc)
                .type(TransactionType.BUY)
                .quantity(BigDecimal.ONE)
                .pricePerUnit(new BigDecimal("30000"))
                .totalAmount(new BigDecimal("30000"))
                .totalAmountEur(new BigDecimal("30000"))
                .currency("EUR")
                .fees(BigDecimal.ZERO)
                .transactionDate(LocalDateTime.now().minusDays(30))
                .build());

        // Prix présents SEULEMENT à J-30, J-20 et J-10.
        // Le trou entre les deux est volontaire : c'est le cas à tester.
        priceAt("BTC-USD", "30000", 30);
        priceAt("BTC-USD", "35000", 20);
        priceAt("BTC-USD", "40000", 10);
        priceAt("BTC-USD", "42000", 0);
    }

    private void priceAt(String symbol, String price, int daysAgo) {
        assetPriceRepository.save(AssetPrice.builder()
                .symbol(symbol)
                .price(new BigDecimal(price))
                .currency("EUR")
                .lastUpdated(LocalDateTime.now().minusDays(daysAgo))
                .build());
    }

    @Test
    @DisplayName("Aucun snapshot ne vaut 0 alors qu'une position est détenue")
    void aucunFauxDecrochage() {
        historyService.rebuild(portfolioId, LocalDate.now().minusDays(30));

        List<PortfolioSnapshot> snapshots = snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId);

        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots)
                .as("Un snapshot à 0 sur une position détenue = faux décrochage de la courbe")
                .allSatisfy(s -> assertThat(s.getTotalValue()).isGreaterThan(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Les dates sans prix reprennent le dernier prix connu, pas 0")
    void reportDuDernierPrixConnu() {
        historyService.rebuild(portfolioId, LocalDate.now().minusDays(30));

        List<PortfolioSnapshot> snapshots = snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId);

        // Entre J-20 et J-11, le seul prix connu est 35 000 : il doit être reporté
        assertThat(snapshots)
                .filteredOn(s -> {
                    long d = java.time.temporal.ChronoUnit.DAYS.between(
                            s.getSnapshotDate(), java.time.LocalDate.now());
                    return d >= 11 && d <= 20;
                })
                .allSatisfy(s ->
                        assertThat(s.getTotalValue()).isEqualByComparingTo("35000.00"));
    }

    @Test
    @DisplayName("Aucun snapshot antérieur à la première transaction")
    void pasDeSnapshotAvantLaPremiereTransaction() {
        historyService.rebuild(portfolioId, LocalDate.now().minusDays(90));

        assertThat(snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId))
                .allSatisfy(s -> assertThat(s.getSnapshotDate())
                        .isAfterOrEqualTo(java.time.LocalDate.now().minusDays(30)));
    }

    @Test
    @DisplayName("Idempotence : relancer le backfill ne duplique pas les snapshots")
    void idempotence() {
        historyService.rebuild(portfolioId, LocalDate.now().minusDays(30));
        int apresPremierPassage = snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId).size();

        historyService.rebuild(portfolioId, LocalDate.now().minusDays(30));
        int apresSecondPassage = snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId).size();

        assertThat(apresSecondPassage).isEqualTo(apresPremierPassage);
    }

    @Test
    @DisplayName("La courbe est strictement croissante en dates, sans doublon")
    void courbeSansDoublonDeDate() {
        historyService.rebuild(portfolioId, LocalDate.now().minusDays(30));

        List<PortfolioSnapshot> snapshots = snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId);

        assertThat(snapshots)
                .extracting(PortfolioSnapshot::getSnapshotDate)
                .doesNotHaveDuplicates();
    }
}
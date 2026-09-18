package com.portfolio.tracker.transaction;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("findAllForValuation — comportement sur PostgreSQL réel")
class TransactionRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private UserRepository userRepository;

    private UUID userId;
    private UUID pea;
    private UUID cto;

    @BeforeEach
    void seed() {
        User user = userRepository.save(User.builder()
                .email("test-" + UUID.randomUUID() + "@fym.io")
                .username("test-"+ UUID.randomUUID())
                .password("{noop}irrelevant")
                .build());
        userId = user.getId();

        Portfolio p1 = portfolioRepository.save(
                Portfolio.builder().name("PEA").user(user).type(PortfolioType.PEA).build());
        Portfolio p2 = portfolioRepository.save(
                Portfolio.builder().name("CTO").user(user).type(PortfolioType.CTO).build());
        pea = p1.getId();
        cto = p2.getId();

        Asset btc = assetRepository.save(Asset.builder()
                .symbol("BTC-USD").name("Bitcoin").portfolio(p1).build());
        Asset eth = assetRepository.save(Asset.builder()
                .symbol("ETH-USD").name("Ethereum").portfolio(p2).build());

        transactionRepository.save(buy(btc, "1", "30000", 30));
        transactionRepository.save(buy(btc, "1", "35000", 10));
        transactionRepository.save(buy(eth, "10", "1000", 20));
    }

    private Transaction buy(Asset asset, String qty, String price, int daysAgo) {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal p = new BigDecimal(price);
        return Transaction.builder()
                .asset(asset)
                .type(TransactionType.BUY)
                .quantity(q)
                .pricePerUnit(p)
                .currency("EUR")
                .totalAmount(q.multiply(p))
                .totalAmountEur(q.multiply(p))
                .fees(BigDecimal.ZERO)
                .transactionDate(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    @Test
    @DisplayName("portfolioId NULL → toutes les transactions, sans erreur de typage PostgreSQL")
    void portfolioIdNull() {
        // C'EST LE TEST CLÉ : reproduit l'appel du dashboard global
        List<Transaction> result =
                transactionRepository.findAllForValuation(userId, null, null);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("portfolioId renseigné → filtrage effectif")
    void filtreParPortefeuille() {
        assertThat(transactionRepository.findAllForValuation(userId, pea, null)).hasSize(2);
        assertThat(transactionRepository.findAllForValuation(userId, cto, null)).hasSize(1);
    }

    @Test
    @DisplayName("asOf NULL et asOf renseigné → borne temporelle effective")
    void borneTemporelle() {
        LocalDateTime asOf = LocalDateTime.now().minusDays(15);

        assertThat(transactionRepository.findAllForValuation(userId, null, null)).hasSize(3);
        // Seules les transactions de J-30 et J-20 sont antérieures à J-15
        assertThat(transactionRepository.findAllForValuation(userId, null, asOf)).hasSize(2);
    }

    @Test
    @DisplayName("Les deux filtres NULL simultanément ne cassent pas la requête")
    void deuxFiltresNull() {
        assertThat(transactionRepository.findAllForValuation(userId, null, null))
                .isNotEmpty();
    }

    @Test
    @DisplayName("Isolation : un autre utilisateur ne voit rien")
    void isolationParUtilisateur() {
        assertThat(transactionRepository.findAllForValuation(UUID.randomUUID(), null, null))
                .isEmpty();
    }

    @Test
    @DisplayName("JOIN FETCH : asset et portfolio hydratés, aucun lazy loading résiduel")
    void joinFetchHydrate() {
        List<Transaction> result =
                transactionRepository.findAllForValuation(userId, null, null);

        // Si le JOIN FETCH sautait, ces accès déclencheraient des requêtes (N+1)
        assertThat(result).allSatisfy(t -> {
            assertThat(t.getAsset().getSymbol()).isNotBlank();
            assertThat(t.getAsset().getPortfolio().getName()).isNotBlank();
        });
    }

    @Test
    @DisplayName("Tri chronologique ascendant garanti — prérequis du calcul CUMP")
    void triChronologique() {
        List<Transaction> result =
                transactionRepository.findAllForValuation(userId, pea, null);

        assertThat(result)
                .extracting(Transaction::getTransactionDate)
                .isSorted();
    }
}
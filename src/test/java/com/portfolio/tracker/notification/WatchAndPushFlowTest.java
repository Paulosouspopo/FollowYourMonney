package com.portfolio.tracker.notification;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.assetprice.AssetPriceService;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.notification.alert.AlertEvaluator;
import com.portfolio.tracker.notification.alert.AlertRule;
import com.portfolio.tracker.notification.alert.AlertRuleService;
import com.portfolio.tracker.notification.dto.AlertRuleRequest;
import com.portfolio.tracker.notification.push.PushService;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.TimeZones;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceAlreadyExistsException;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import com.portfolio.tracker.watchlist.WatchlistService;
import com.portfolio.tracker.watchlist.dto.MarketDetailResponse;
import com.portfolio.tracker.watchlist.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Alertes enrichies, actifs suivis et canal push. Yahoo simulé : WATCH cotait 100 € jusqu'à hier. */
@DisplayName("Actifs suivis, alertes enrichies, push")
class WatchAndPushFlowTest extends AbstractIntegrationTest {

    @Autowired private AlertRuleService ruleService;
    @Autowired private AlertEvaluator evaluator;
    @Autowired private NotificationService notificationService;
    @Autowired private WatchlistService watchlistService;
    @Autowired private TransactionService transactionService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetPriceService assetPriceService;

    @MockitoBean private PushService pushService;

    private final LocalDate today = TimeZones.todayForUser();
    private final AtomicReference<BigDecimal> watchNow = new AtomicReference<>(new BigDecimal("100"));
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .email("watch-" + UUID.randomUUID() + "@fym.io")
                .username("watch-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .emailVerified(true)
                .build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("WATCH")).thenAnswer(inv -> Optional.of(new MarketQuote("WATCH",
                watchNow.get(), "EUR", LocalDateTime.now(), today, "Watch Corp", "Paris", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Plus haut sur 1 an : déclenché au-dessus du record précédent, avec le nom choisi")
    void plusHaut() {
        ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.NEW_HIGH, null, "Record WATCH"), user.getId());
        ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.NEW_LOW, null, null), user.getId());

        assertThat(evaluate()).isZero(); // 100 = record égalé, pas dépassé
        watchNow.set(new BigDecimal("110"));
        assertThat(evaluate()).isEqualTo(1);
        Notification n = notificationService.recent(user.getId(), 1).get(0);
        assertThat(n.getTitle()).isEqualTo("🔔 Record WATCH");
        assertThat(n.getBody()).contains("au plus haut sur 1 an").contains("+10");
        assertThat(n.getLink()).isEqualTo("/markets/WATCH");
    }

    @Test
    @DisplayName("Sourdine : la règle n'est pas évaluée avant la date choisie")
    void sourdine() {
        UUID id = ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.ABOVE, "105", null), user.getId()).id();
        ruleService.mute(id, LocalDateTime.now().plusDays(1), user.getId());
        watchNow.set(new BigDecimal("120"));
        assertThat(evaluate()).isZero();

        ruleService.mute(id, null, user.getId());
        assertThat(evaluate()).isEqualTo(1);
    }

    @Test
    @DisplayName("Plus-value latente et poids : mesurés sur les positions détenues seulement")
    void plusValueEtPoids() {
        ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.PROFIT_ABOVE, "20", null), user.getId());
        assertThat(evaluate()).isZero(); // pas détenu : rien à mesurer

        UUID pf = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
        transactionService.create(pf, new TransactionCreateRequest("WATCH", TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal("100"), null, "EUR", today.minusDays(3).atStartOfDay(), null), user.getId());
        ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.WEIGHT_ABOVE, "50", null), user.getId());

        watchNow.set(new BigDecimal("130"));
        assetPriceService.updateAllAssetPrices(); // comme le job horaire : cours enregistré, puis évaluation
        assertThat(evaluate()).isEqualTo(2); // +30 % ≥ 20 % ; 100 % du patrimoine ≥ 50 %
        assertThat(notificationService.recent(user.getId(), 10)).extracting(Notification::getTitle)
                .anySatisfy(t -> assertThat(t).contains("+30").contains("plus-value"))
                .anySatisfy(t -> assertThat(t).contains("pèse 100"));
    }

    @Test
    @DisplayName("Combinaisons incohérentes refusées")
    void validation() {
        assertThatThrownBy(() -> ruleService.create(rule(AlertRule.Scope.GLOBAL, AlertRule.Condition.NEW_HIGH, null, null),
                user.getId())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.WEIGHT_ABOVE, "150",
                null), user.getId())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ruleService.create(rule(AlertRule.Scope.ASSET, AlertRule.Condition.ABOVE, null, null),
                user.getId())).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Push : envoyé par défaut, pas si l'utilisateur l'a coupé ; l'app garde la notification")
    void push() {
        notificationService.notify(user.getId(), Notification.Type.ALERT, "Titre", "Corps", "/", false);
        verify(pushService).send(eq(user.getId()), eq("Titre"), eq("Corps"), eq("/"), contains("ALERT:"));

        notificationService.updatePreferences(user.getId(), false, 22, 7);
        notificationService.notify(user.getId(), Notification.Type.ALERT, "Muet", "Corps", "/", false);
        verify(pushService, never()).send(any(), eq("Muet"), any(), any(), any());
        assertThat(notificationService.unreadCount(user.getId())).isEqualTo(2);

        assertThatThrownBy(() -> notificationService.updatePreferences(user.getId(), true, 22, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Heures calmes, y compris sur une plage qui passe minuit")
    void heuresCalmes() {
        NotificationPreferences night = NotificationPreferences.builder().quietStart(22).quietEnd(7).build();
        assertThat(night.isQuiet(LocalTime.of(23, 30))).isTrue();
        assertThat(night.isQuiet(LocalTime.of(6, 59))).isTrue();
        assertThat(night.isQuiet(LocalTime.of(7, 0))).isFalse();
        assertThat(night.isQuiet(LocalTime.of(12, 0))).isFalse();

        NotificationPreferences lunch = NotificationPreferences.builder().quietStart(12).quietEnd(14).build();
        assertThat(lunch.isQuiet(LocalTime.of(13, 0))).isTrue();
        assertThat(lunch.isQuiet(LocalTime.of(14, 0))).isFalse();
        assertThat(NotificationPreferences.defaults(UUID.randomUUID()).isQuiet(LocalTime.NOON)).isFalse();
    }

    @Test
    @DisplayName("Actifs suivis : ajout (historique d'un an), doublon refusé, fiche avec records, retrait")
    void watchlist() {
        WatchlistItemResponse item = watchlistService.add(user.getId(), "watch");
        assertThat(item.symbol()).isEqualTo("WATCH");
        assertThat(item.name()).isEqualTo("Watch Corp");
        assertThat(item.price()).isEqualByComparingTo("100");
        assertThat(item.sparkline()).hasSizeGreaterThan(20);
        assertThat(item.ownedQuantity()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> watchlistService.add(user.getId(), "WATCH"))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        assertThatThrownBy(() -> watchlistService.add(user.getId(), "INCONNU"))
                .isInstanceOf(BadRequestException.class);

        watchNow.set(new BigDecimal("95"));
        MarketDetailResponse detail = watchlistService.detail(user.getId(), "WATCH");
        assertThat(detail.watchlistId()).isEqualTo(item.id());
        assertThat(detail.high52w()).isEqualByComparingTo("100");
        assertThat(detail.low52w()).isEqualByComparingTo("95");
        assertThat(detail.dayChangePct()).isEqualByComparingTo("-5");
        assertThat(detail.holdings()).isEmpty();
        assertThat(watchlistService.history("WATCH", "1M")).hasSizeGreaterThan(20);

        watchlistService.remove(item.id(), user.getId());
        assertThat(watchlistService.list(user.getId())).isEmpty();
    }

    /** Alertes reçues par l'utilisateur du test (la base est partagée avec les règles d'autres tests). */
    private long evaluate() {
        long before = notificationService.unreadCount(user.getId());
        evaluator.evaluateAll();
        return notificationService.unreadCount(user.getId()) - before;
    }

    private static AlertRuleRequest rule(AlertRule.Scope scope, AlertRule.Condition condition, String threshold,
            String label) {
        return new AlertRuleRequest(scope, null, scope == AlertRule.Scope.ASSET ? "WATCH" : null, condition,
                threshold != null ? new BigDecimal(threshold) : null, null, false, true, label, null, null);
    }
}

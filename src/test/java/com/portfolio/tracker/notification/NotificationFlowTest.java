package com.portfolio.tracker.notification;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.notification.alert.AlertEvaluator;
import com.portfolio.tracker.notification.alert.AlertRule;
import com.portfolio.tracker.notification.alert.AlertRuleService;
import com.portfolio.tracker.notification.dto.AlertRuleRequest;
import com.portfolio.tracker.notification.dto.ReportPreview;
import com.portfolio.tracker.notification.dto.ReportSettingsDto;
import com.portfolio.tracker.notification.report.ReportService;
import com.portfolio.tracker.notification.report.ReportSettings;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.TimeZones;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.mail.EmailSender;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Alertes et rapports, via les vrais services. Yahoo simulé : FAKE cotait 100 € jusqu'à hier. */
@DisplayName("Notifications — alertes et rapports")
class NotificationFlowTest extends AbstractIntegrationTest {

    @Autowired private AlertRuleService ruleService;
    @Autowired private AlertEvaluator evaluator;
    @Autowired private NotificationService notificationService;
    @Autowired private ReportService reportService;
    @Autowired private TransactionService transactionService;
    @Autowired private CashMovementService cashMovementService;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private EmailSender emailSender;

    private final LocalDate today = TimeZones.todayForUser();
    /** Cours actuel de FAKE (modifiable par test). */
    private final AtomicReference<BigDecimal> fakeNow = new AtomicReference<>(new BigDecimal("100"));
    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .email("notif-" + UUID.randomUUID() + "@fym.io")
                .username("notif-" + UUID.randomUUID())
                .password("{noop}irrelevant")
                .emailVerified(true)
                .build());
        when(marketDataProvider.search(anyString())).thenReturn(List.of());
        when(marketDataProvider.getQuote(anyString())).thenReturn(Optional.empty());
        when(marketDataProvider.getQuote("FAKE")).thenAnswer(inv -> Optional.of(new MarketQuote("FAKE", fakeNow.get(),
                "EUR", LocalDateTime.now(), today, "Fake Corp", "Paris", "EQUITY")));
        when(marketDataProvider.getDailyHistory(anyString(), any(), any())).thenAnswer(inv -> {
            List<MarketPricePoint> points = new ArrayList<>();
            for (LocalDate d = inv.getArgument(1); !d.isAfter(inv.getArgument(2)) && d.isBefore(today); d = d.plusDays(1)) {
                points.add(new MarketPricePoint(inv.getArgument(0), new BigDecimal("100"), "EUR", d, d.atTime(17, 30)));
            }
            return points;
        });
    }

    @Test
    @DisplayName("Actif +10 % sur la journée : une alerte, pas de répétition, réarmée quand la condition retombe")
    void alerteActif() {
        ruleService.create(assetRule(AlertRule.Condition.RISES, "5"), user.getId());

        fakeNow.set(new BigDecimal("110"));
        assertThat(evaluator.evaluateAll()).isEqualTo(1);
        List<Notification> inbox = notificationService.recent(user.getId(), 10);
        assertThat(inbox).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(Notification.Type.ALERT);
            assertThat(n.getTitle()).contains("FAKE").contains("+10");
        });

        assertThat(evaluator.evaluateAll()).isZero(); // toujours +10 % : pas de rappel
        fakeNow.set(new BigDecimal("101"));
        assertThat(evaluator.evaluateAll()).isZero(); // condition retombée : réarmée
        fakeNow.set(new BigDecimal("108"));
        assertThat(evaluator.evaluateAll()).isEqualTo(1);
        assertThat(notificationService.unreadCount(user.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Seuil de cours au-dessus de 150 € ; email envoyé si demandé")
    void seuilEtEmail() {
        AlertRuleRequest above = new AlertRuleRequest(AlertRule.Scope.ASSET, null, "FAKE", AlertRule.Condition.ABOVE,
                new BigDecimal("150"), null, true, true);
        ruleService.create(above, user.getId());

        assertThat(evaluator.evaluateAll()).isZero();
        fakeNow.set(new BigDecimal("151"));
        assertThat(evaluator.evaluateAll()).isEqualTo(1);
        verify(emailSender).send(eq(user.getEmail()), contains("au-dessus de 150"), anyString());
    }

    @Test
    @DisplayName("Portefeuille : une baisse de cours déclenche, un versement ne déclenche pas de fausse hausse")
    void portefeuilleNeutreAuxVersements() {
        UUID pf = portfolioRepository.save(Portfolio.builder().name("CTO").type(PortfolioType.CTO).user(user)
                .cashTracking(true).build()).getId();
        fakeNow.set(new BigDecimal("90")); // cours du jour ; 100 € jusqu'à hier
        cashMovementService.create(pf, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("1000"),
                today.minusDays(6), null), user.getId());
        transactionService.create(pf, new TransactionCreateRequest("FAKE", TransactionType.BUY, BigDecimal.TEN,
                new BigDecimal("100"), null, "EUR", today.minusDays(5).atStartOfDay(), null), user.getId());

        ruleService.create(portfolioRule(pf, AlertRule.Condition.FALLS), user.getId());
        ruleService.create(portfolioRule(pf, AlertRule.Condition.RISES), user.getId());

        assertThat(evaluator.evaluateAll()).isEqualTo(1); // -100 € de plus-value sur 1000 € : -10 %
        assertThat(notificationService.recent(user.getId(), 5).get(0).getTitle()).contains("CTO").contains("−10");

        cashMovementService.create(pf, new CashMovementRequest(CashMovementType.DEPOSIT, new BigDecimal("5000"),
                today, null), user.getId());
        assertThat(evaluator.evaluateAll()).isZero(); // +5000 € de valeur, mais 0 de plus-value
    }

    @Test
    @DisplayName("Règles invalides refusées")
    void validation() {
        assertThatThrownBy(() -> ruleService.create(assetRule(AlertRule.Condition.FALLS, "5000"), user.getId()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ruleService.create(new AlertRuleRequest(AlertRule.Scope.ASSET, null, "INCONNU",
                AlertRule.Condition.RISES, BigDecimal.ONE, AlertRule.Period.DAY, false, true), user.getId()))
                .isInstanceOf(BadRequestException.class);
        assertThat(ruleService.create(new AlertRuleRequest(AlertRule.Scope.GLOBAL, null, null, AlertRule.Condition.MOVES,
                new BigDecimal("2"), null, false, true), user.getId()).description())
                .isEqualTo("Patrimoine total varie de 2 % sur 1 jour");
    }

    @Test
    @DisplayName("Rapport quotidien : envoyé une fois à l'heure choisie, contenu lisible")
    void rapport() {
        UUID pf = portfolioRepository.save(Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build()).getId();
        transactionService.create(pf, new TransactionCreateRequest("FAKE", TransactionType.BUY, BigDecimal.ONE,
                new BigDecimal("100"), null, "EUR", today.minusDays(3).atStartOfDay(), null), user.getId());

        reportService.updateSettings(user.getId(), new ReportSettingsDto(ReportSettings.Frequency.DAILY,
                TimeZones.nowForUser().getHour(), false));
        assertThat(reportService.sendDueReports()).isEqualTo(1);
        assertThat(reportService.sendDueReports()).isZero(); // déjà envoyé aujourd'hui

        Notification report = notificationService.recent(user.getId(), 5).get(0);
        assertThat(report.getType()).isEqualTo(Notification.Type.REPORT);
        assertThat(report.getBody()).contains("Patrimoine").contains("PEA");

        ReportPreview preview = reportService.preview(user.getId(), ReportSettings.Frequency.WEEKLY);
        assertThat(preview.title()).startsWith("Ton bilan de la semaine");
    }

    // ------------------------------------------------------------------ utils

    private static AlertRuleRequest assetRule(AlertRule.Condition condition, String threshold) {
        return new AlertRuleRequest(AlertRule.Scope.ASSET, null, "FAKE", condition, new BigDecimal(threshold),
                AlertRule.Period.DAY, false, true);
    }

    private static AlertRuleRequest portfolioRule(UUID portfolioId, AlertRule.Condition condition) {
        return new AlertRuleRequest(AlertRule.Scope.PORTFOLIO, portfolioId, null, condition, new BigDecimal("3"),
                AlertRule.Period.DAY, false, true);
    }
}

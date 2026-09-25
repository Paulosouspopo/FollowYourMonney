package com.portfolio.tracker.notification.alert;

import com.portfolio.tracker.assetprice.MarketPriceLookup;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.notification.Formats;
import com.portfolio.tracker.notification.Notification;
import com.portfolio.tracker.notification.NotificationService;
import com.portfolio.tracker.shared.TimeZones;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Évalue les règles d'alerte (après chaque mise à jour horaire des cours).
 *
 * Mesures :
 * <ul>
 * <li>actif : cours en EUR maintenant vs clôture d'il y a 1 / 7 / 30 jours ;</li>
 * <li>portefeuille / patrimoine : variation de la PLUS-VALUE rapportée à la
 * valeur de départ. Un versement, un achat ou une vente ne change pas la
 * plus-value : ils ne déclenchent donc pas de fausse « hausse de 10 % » ;</li>
 * <li>seuils ABOVE / BELOW : valeur ou cours actuel en EUR ;</li>
 * <li>PROFIT_ABOVE / LOSS_BELOW : plus-value latente / prix de revient des
 * positions (un actif non détenu n'a pas de mesure) ;</li>
 * <li>NEW_HIGH / NEW_LOW : cours actuel (devise de cotation) vs plus haut /
 * plus bas des clôtures de la période, mesuré en % d'écart ;</li>
 * <li>WEIGHT_ABOVE : valeur de l'actif (toutes lignes) ou du portefeuille /
 * patrimoine total.</li>
 * </ul>
 * Anti-répétition : une règle déclenchée est désarmée jusqu'à ce que sa
 * condition redevienne fausse. Pour une variation sur 1 jour ou un record,
 * elle est aussi réarmée chaque nouveau jour (une nouvelle baisse de 3 %
 * demain, un nouveau record, sont de nouvelles informations).
 * Une règle en sourdine ({@code mutedUntil} futur) n'est pas évaluée.
 */
@Component
@Slf4j
public class AlertEvaluator {

    private final AlertRuleRepository ruleRepository;
    private final PortfolioValuationService valuationService;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final MarketPriceLookup priceLookup;
    private final MarketDataProvider marketDataProvider;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public AlertEvaluator(AlertRuleRepository ruleRepository,
            PortfolioValuationService valuationService,
            PortfolioSnapshotRepository snapshotRepository,
            MarketPriceLookup priceLookup,
            MarketDataProvider marketDataProvider,
            NotificationService notificationService,
            PlatformTransactionManager transactionManager) {
        this.ruleRepository = ruleRepository;
        this.valuationService = valuationService;
        this.snapshotRepository = snapshotRepository;
        this.priceLookup = priceLookup;
        this.marketDataProvider = marketDataProvider;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Mesure courante d'une règle, décrite pour le message. */
    record Measure(BigDecimal value, String detail) {
    }

    /** @return nombre d'alertes envoyées */
    public int evaluateAll() {
        List<AlertRule> rules = tx.execute(s -> ruleRepository.findAllEnabled());
        if (rules == null || rules.isEmpty()) {
            return 0;
        }
        Map<UUID, List<AlertRule>> byUser = rules.stream()
                .collect(Collectors.groupingBy(AlertRule::getUserId, LinkedHashMap::new, Collectors.toList()));
        int fired = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<UUID, List<AlertRule>> entry : byUser.entrySet()) {
            UserContext ctx = new UserContext(entry.getKey());
            for (AlertRule rule : entry.getValue()) {
                if (rule.getMutedUntil() != null && rule.getMutedUntil().isAfter(now)) {
                    continue;
                }
                try {
                    if (evaluate(rule, ctx)) {
                        fired++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Alerte {} non évaluée : {}", rule.getId(), e.getMessage());
                }
            }
        }
        if (fired > 0) {
            log.info("Alertes : {} déclenchée(s) sur {} règle(s)", fired, rules.size());
        }
        return fired;
    }

    /** @return true si l'alerte a été envoyée */
    boolean evaluate(AlertRule rule, UserContext ctx) {
        LocalDate today = TimeZones.todayForUser();
        Optional<Measure> measure = measure(rule, ctx, today);
        if (measure.isEmpty()) {
            return false; // données insuffisantes (pas encore d'historique, cours introuvable)
        }
        BigDecimal v = measure.get().value();
        BigDecimal t = rule.getThreshold();
        boolean met = switch (rule.getCondition()) {
            case RISES -> v.compareTo(t) >= 0;
            case FALLS -> v.compareTo(t.negate()) <= 0;
            case MOVES -> v.abs().compareTo(t) >= 0;
            case ABOVE, PROFIT_ABOVE, WEIGHT_ABOVE -> v.compareTo(t) >= 0;
            case BELOW -> v.compareTo(t) <= 0;
            case LOSS_BELOW -> v.compareTo(t.negate()) <= 0;
            case NEW_HIGH -> v.signum() > 0;
            case NEW_LOW -> v.signum() < 0;
        };

        boolean daily = rule.getPeriod() == AlertRule.Period.DAY || rule.getCondition().isExtreme();
        boolean armed = rule.isArmed()
                || (daily && rule.getLastTriggeredAt() != null
                        && rule.getLastTriggeredAt().toLocalDate().isBefore(today));
        if (met && armed) {
            fire(rule, measure.get());
            return true;
        }
        if (!met && !rule.isArmed()) {
            tx.executeWithoutResult(s -> ruleRepository.findById(rule.getId()).ifPresent(r -> r.setArmed(true)));
        }
        return false;
    }

    private void fire(AlertRule rule, Measure measure) {
        String auto = title(rule, measure.value());
        String title = rule.getLabel() != null ? "🔔 " + rule.getLabel() : auto;
        String body = (rule.getLabel() != null ? auto + "\n" : "") + measure.detail()
                + "\n\nAlerte : " + AlertRuleDescriber.describe(rule) + ".";
        String link = switch (rule.getScope()) {
            case PORTFOLIO -> rule.getPortfolio() != null ? "/portfolios/" + rule.getPortfolio().getId() : "/";
            case ASSET -> "/markets/" + java.net.URLEncoder.encode(rule.getSymbol(), java.nio.charset.StandardCharsets.UTF_8);
            case GLOBAL -> "/";
        };
        tx.executeWithoutResult(s -> {
            ruleRepository.findById(rule.getId()).ifPresent(r -> {
                r.setArmed(false);
                r.setLastTriggeredAt(LocalDateTime.now());
            });
            notificationService.notify(rule.getUserId(), Notification.Type.ALERT, title, body, link,
                    rule.isNotifyEmail(), rule.isNotifyPush());
        });
    }

    private static String title(AlertRule rule, BigDecimal value) {
        String subject = rule.getScope() == AlertRule.Scope.ASSET ? rule.getSymbol() : AlertRuleDescriber.subject(rule);
        return switch (rule.getCondition()) {
            case RISES, FALLS, MOVES -> {
                String icon = value.signum() >= 0 ? "📈" : "📉";
                String when = rule.getPeriod() == AlertRule.Period.DAY ? "aujourd'hui"
                        : AlertRuleDescriber.period(rule.getPeriod());
                yield icon + " " + subject + " " + Formats.signedPercent(value) + " " + when;
            }
            case ABOVE -> "🎯 " + subject + " au-dessus de " + Formats.eur(rule.getThreshold());
            case BELOW -> "🎯 " + subject + " en dessous de " + Formats.eur(rule.getThreshold());
            case PROFIT_ABOVE -> "💰 " + subject + " : " + Formats.signedPercent(value) + " de plus-value";
            case LOSS_BELOW -> "⚠️ " + subject + " : " + Formats.signedPercent(value) + " de moins-value";
            case NEW_HIGH -> "🚀 " + subject + " au plus haut " + AlertRuleDescriber.period(rule.getPeriod());
            case NEW_LOW -> "🕳️ " + subject + " au plus bas " + AlertRuleDescriber.period(rule.getPeriod());
            case WEIGHT_ABOVE -> "⚖️ " + subject + " pèse " + Formats.percent(value.setScale(1, RoundingMode.HALF_UP))
                    + " du patrimoine";
        };
    }

    // ------------------------------------------------------------------ mesures

    private Optional<Measure> measure(AlertRule rule, UserContext ctx, LocalDate today) {
        switch (rule.getCondition()) {
            case NEW_HIGH, NEW_LOW -> {
                return measureExtreme(rule, today);
            }
            case PROFIT_ABOVE, LOSS_BELOW -> {
                return measureLatent(rule, ctx);
            }
            case WEIGHT_ABOVE -> {
                return measureWeight(rule, ctx);
            }
            default -> {
                // variation ou seuil en EUR
            }
        }
        return switch (rule.getScope()) {
            case ASSET -> measureAsset(rule, today);
            case PORTFOLIO -> ctx.portfolio(rule.getPortfolio().getId())
                    .flatMap(p -> measureHolding(rule, List.of(p), today));
            case GLOBAL -> measureHolding(rule, ctx.valuation().getPortfolios(), today);
        };
    }

    private Optional<Measure> measureAsset(AlertRule rule, LocalDate today) {
        Optional<BigDecimal> now = priceLookup.priceInEur(rule.getSymbol(), today);
        if (now.isEmpty()) {
            return Optional.empty();
        }
        if (!rule.getCondition().isPercentage()) {
            return Optional.of(new Measure(now.get(), "Cours actuel : " + Formats.eur(now.get()) + "."));
        }
        LocalDate baseDay = today.minusDays(rule.getPeriod().days());
        return priceLookup.priceInEur(rule.getSymbol(), baseDay)
                .filter(base -> base.signum() > 0)
                .map(base -> {
                    BigDecimal pct = now.get().subtract(base).multiply(BigDecimal.valueOf(100))
                            .divide(base, 4, RoundingMode.HALF_UP);
                    return new Measure(pct, "Cours : " + Formats.eur(now.get()) + " (contre " + Formats.eur(base)
                            + " " + since(rule.getPeriod()) + ").");
                });
    }

    /** Portefeuille(s) : variation de plus-value / valeur de départ (neutre vis-à-vis des versements). */
    private Optional<Measure> measureHolding(AlertRule rule, List<PortfolioValuation> portfolios, LocalDate today) {
        BigDecimal value = portfolios.stream().map(PortfolioValuation::getCurrentValueEur)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!rule.getCondition().isPercentage()) {
            return Optional.of(new Measure(value, "Valeur actuelle : " + Formats.eur(value) + "."));
        }
        BigDecimal gainNow = portfolios.stream()
                .map(p -> p.getCurrentValueEur().subtract(p.getInvestedEur()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate baseDay = today.minusDays(rule.getPeriod().days());
        BigDecimal baseValue = BigDecimal.ZERO;
        BigDecimal baseGain = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioValuation p : portfolios) {
            Optional<PortfolioSnapshot> snap = tx.execute(s -> snapshotRepository
                    .findTopByPortfolioIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(p.getPortfolioId(), baseDay));
            if (snap != null && snap.isPresent()) {
                any = true;
                baseValue = baseValue.add(snap.get().getTotalValue());
                baseGain = baseGain.add(snap.get().getGainLoss());
            }
        }
        if (!any || baseValue.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal delta = gainNow.subtract(baseGain);
        BigDecimal pct = delta.multiply(BigDecimal.valueOf(100)).divide(baseValue, 4, RoundingMode.HALF_UP);
        return Optional.of(new Measure(pct, "Valeur : " + Formats.eur(value) + " (" + Formats.signedEur(delta)
                + " de plus-value " + since(rule.getPeriod()) + ")."));
    }

    /** Écart en % entre le cours actuel et le record précédent de la période (devise de cotation). */
    private Optional<Measure> measureExtreme(AlertRule rule, LocalDate today) {
        Optional<MarketQuote> quote = marketDataProvider.getQuote(rule.getSymbol());
        if (quote.isEmpty() || quote.get().price() == null || quote.get().price().signum() <= 0) {
            return Optional.empty();
        }
        MarketQuote q = quote.get();
        LocalDate from = q.marketDate().minusDays(rule.getPeriod().days());
        return priceLookup.closingRange(rule.getSymbol(), from, q.marketDate())
                .filter(r -> r.days() >= Math.min(5, rule.getPeriod().days()))
                .map(r -> {
                    boolean high = rule.getCondition() == AlertRule.Condition.NEW_HIGH;
                    BigDecimal record = high ? r.high() : r.low();
                    BigDecimal pct = q.price().subtract(record).multiply(BigDecimal.valueOf(100))
                            .divide(record, 4, RoundingMode.HALF_UP);
                    return new Measure(pct, "Cours : " + Formats.money(q.price(), q.currency()) + " (précédent "
                            + (high ? "plus haut" : "plus bas") + " " + AlertRuleDescriber.period(rule.getPeriod())
                            + " : " + Formats.money(record, r.currency()) + ", " + Formats.signedPercent(pct) + ").");
                });
    }

    /** Plus-value latente / prix de revient des positions concernées. */
    private Optional<Measure> measureLatent(AlertRule rule, UserContext ctx) {
        List<PositionValuation> positions = positions(rule, ctx);
        BigDecimal cost = positions.stream().map(PositionValuation::getInvestedEur).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cost.signum() <= 0) {
            return Optional.empty(); // actif non détenu, portefeuille vide
        }
        BigDecimal gain = positions.stream().map(PositionValuation::getUnrealizedGainEur)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pct = gain.multiply(BigDecimal.valueOf(100)).divide(cost, 4, RoundingMode.HALF_UP);
        return Optional.of(new Measure(pct, "Plus-value latente : " + Formats.signedEur(gain) + " ("
                + Formats.signedPercent(pct) + ") pour " + Formats.eur(cost) + " investis."));
    }

    private Optional<Measure> measureWeight(AlertRule rule, UserContext ctx) {
        BigDecimal total = ctx.valuation().getTotalValueEur();
        if (total == null || total.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal part = rule.getScope() == AlertRule.Scope.PORTFOLIO
                ? ctx.portfolio(rule.getPortfolio().getId()).map(PortfolioValuation::getCurrentValueEur).orElse(null)
                : positions(rule, ctx).stream().map(PositionValuation::getCurrentValueEur)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (part == null || part.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal pct = part.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP);
        return Optional.of(new Measure(pct, "Valeur : " + Formats.eur(part) + " sur " + Formats.eur(total)
                + " de patrimoine."));
    }

    /** Positions ouvertes du périmètre (un actif peut être détenu dans plusieurs portefeuilles). */
    private static List<PositionValuation> positions(AlertRule rule, UserContext ctx) {
        List<PortfolioValuation> portfolios = switch (rule.getScope()) {
            case PORTFOLIO -> ctx.portfolio(rule.getPortfolio().getId()).map(List::of).orElse(List.of());
            case ASSET, GLOBAL -> ctx.valuation().getPortfolios();
        };
        return portfolios.stream()
                .flatMap(p -> p.getPositions() == null ? java.util.stream.Stream.empty() : p.getPositions().stream())
                .filter(p -> p.getQuantity() != null && p.getQuantity().signum() > 0)
                .filter(p -> rule.getScope() != AlertRule.Scope.ASSET || rule.getSymbol().equals(p.getSymbol()))
                .toList();
    }

    private static String since(AlertRule.Period period) {
        return switch (period) {
            case DAY -> "depuis la veille";
            case WEEK -> "sur 7 jours";
            case MONTH -> "sur 30 jours";
            case YEAR -> "sur 1 an";
        };
    }

    /** Valorisation calculée une fois par utilisateur et par passage. */
    final class UserContext {
        private final UUID userId;
        private ValuationResult valuation;
        private final Map<UUID, PortfolioValuation> byId = new HashMap<>();

        UserContext(UUID userId) {
            this.userId = userId;
        }

        ValuationResult valuation() {
            if (valuation == null) {
                valuation = tx.execute(s -> valuationService.valuate(userId, null, null));
                valuation.getPortfolios().forEach(p -> byId.put(p.getPortfolioId(), p));
            }
            return valuation;
        }

        Optional<PortfolioValuation> portfolio(UUID portfolioId) {
            valuation();
            return Optional.ofNullable(byId.get(portfolioId));
        }
    }
}

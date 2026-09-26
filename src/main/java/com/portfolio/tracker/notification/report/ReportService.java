package com.portfolio.tracker.notification.report;

import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.notification.Formats;
import com.portfolio.tracker.notification.Notification;
import com.portfolio.tracker.notification.NotificationRepository;
import com.portfolio.tracker.notification.NotificationService;
import com.portfolio.tracker.notification.dto.ReportPreview;
import com.portfolio.tracker.notification.dto.ReportSettingsDto;
import com.portfolio.tracker.plan.InvestmentPlanRepository;
import com.portfolio.tracker.shared.TimeZones;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rapport périodique : patrimoine, variation (de plus-value, donc neutre vis-à-vis
 * des versements), variation par portefeuille, plus fortes hausses et baisses,
 * investissements programmés exécutés, alertes déclenchées.
 * Quotidien : depuis la veille ; hebdomadaire (le lundi) : sur 7 jours.
 */
@Service
@Slf4j
public class ReportService {

    private static final int MOVERS = 3;

    private final ReportSettingsRepository settingsRepository;
    private final PortfolioValuationService valuationService;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PriceHistoryService priceHistoryService;
    private final InvestmentPlanRepository planRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate tx;

    public ReportService(ReportSettingsRepository settingsRepository,
            PortfolioValuationService valuationService,
            PortfolioSnapshotRepository snapshotRepository,
            PriceHistoryService priceHistoryService,
            InvestmentPlanRepository planRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.valuationService = valuationService;
        this.snapshotRepository = snapshotRepository;
        this.priceHistoryService = priceHistoryService;
        this.planRepository = planRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // =============================================================== réglages

    public ReportSettingsDto settings(UUID userId) {
        return ReportSettingsDto.of(tx.execute(s -> settingsRepository.findById(userId)
                .orElse(ReportSettings.defaults(userId))));
    }

    public ReportSettingsDto updateSettings(UUID userId, ReportSettingsDto dto) {
        return tx.execute(s -> {
            ReportSettings settings = settingsRepository.findById(userId).orElse(ReportSettings.defaults(userId));
            settings.setFrequency(dto.frequency());
            settings.setSendHour(dto.sendHour());
            settings.setNotifyEmail(dto.notifyEmail());
            return ReportSettingsDto.of(settingsRepository.save(settings));
        });
    }

    // ================================================================= envoi

    /** Appelé chaque heure : envoie les rapports dont c'est l'heure (fuseau de Paris). */
    public int sendDueReports() {
        ZonedDateTime now = TimeZones.nowForUser();
        LocalDate today = now.toLocalDate();
        List<ReportSettings> due = tx.execute(s -> settingsRepository
                .findByFrequencyNotAndSendHour(ReportSettings.Frequency.NONE, now.getHour()));
        int sent = 0;
        for (ReportSettings settings : due == null ? List.<ReportSettings>of() : due) {
            if (today.equals(settings.getLastSentDate())) {
                continue;
            }
            if (settings.getFrequency() == ReportSettings.Frequency.WEEKLY && today.getDayOfWeek() != DayOfWeek.MONDAY) {
                continue;
            }
            try {
                ReportPreview report = build(settings.getUserId(), settings.getFrequency(), today);
                tx.executeWithoutResult(s -> {
                    notificationService.notify(settings.getUserId(), Notification.Type.REPORT, report.title(),
                            report.body(), "/", settings.isNotifyEmail());
                    settingsRepository.findById(settings.getUserId()).ifPresent(st -> st.setLastSentDate(today));
                });
                sent++;
            } catch (RuntimeException e) {
                log.warn("Rapport de l'utilisateur {} non envoyé : {}", settings.getUserId(), e.getMessage());
            }
        }
        return sent;
    }

    /** Aperçu du rapport tel qu'il serait envoyé maintenant (bouton « Voir un exemple »). */
    public ReportPreview preview(UUID userId, ReportSettings.Frequency frequency) {
        return build(userId, frequency == ReportSettings.Frequency.NONE ? ReportSettings.Frequency.DAILY : frequency,
                TimeZones.todayForUser());
    }

    // ============================================================= contenu

    ReportPreview build(UUID userId, ReportSettings.Frequency frequency, LocalDate today) {
        int days = frequency == ReportSettings.Frequency.WEEKLY ? 7 : 1;
        String since = days == 1 ? "depuis hier" : "sur 7 jours";
        LocalDate baseDay = today.minusDays(days);
        ValuationResult valuation = tx.execute(s -> valuationService.valuate(userId, null, null));

        StringBuilder body = new StringBuilder();
        BigDecimal totalDelta = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        List<String> lines = new ArrayList<>();
        for (PortfolioValuation p : valuation.getPortfolios()) {
            Optional<PortfolioSnapshot> base = tx.execute(s -> snapshotRepository
                    .findTopByPortfolioIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(p.getPortfolioId(), baseDay));
            BigDecimal gainNow = p.getCurrentValueEur().subtract(p.getInvestedEur());
            if (base != null && base.isPresent()) {
                BigDecimal delta = gainNow.subtract(base.get().getGainLoss());
                totalDelta = totalDelta.add(delta);
                totalBase = totalBase.add(base.get().getTotalValue());
                lines.add("• " + p.getName() + " : " + Formats.eur(p.getCurrentValueEur()) + " (" + Formats.signedEur(delta) + ")");
            } else {
                lines.add("• " + p.getName() + " : " + Formats.eur(p.getCurrentValueEur()));
            }
        }
        String totalPct = totalBase.signum() > 0
                ? " / " + Formats.signedPercent(totalDelta.multiply(BigDecimal.valueOf(100)).divide(totalBase, 4, RoundingMode.HALF_UP))
                : "";
        body.append("Patrimoine : ").append(Formats.eur(valuation.getTotalValueEur()))
                .append(" (").append(Formats.signedEur(totalDelta)).append(totalPct).append(" ").append(since).append(")\n");
        lines.forEach(l -> body.append(l).append('\n'));

        List<String> movers = movers(valuation, today, baseDay);
        if (!movers.isEmpty()) {
            body.append("\nPlus fortes variations ").append(since).append(" :\n");
            movers.forEach(m -> body.append(m).append('\n'));
        }

        List<String> plans = tx.execute(s -> planRepository.findByUserId(userId).stream()
                .filter(p -> p.getLastExecutionDate() != null && p.getLastExecutionDate().isAfter(baseDay))
                .map(p -> "• " + Formats.eur(p.getAmount()) + " — " + (p.getSymbol() != null ? p.getName() : "versement sur " + p.getPortfolio().getName()))
                .toList());
        if (plans != null && !plans.isEmpty()) {
            body.append("\nInvestissements programmés exécutés :\n");
            plans.forEach(p -> body.append(p).append('\n'));
        }

        long alerts = notificationRepository.countByUserIdAndTypeAndCreatedAtAfter(userId, Notification.Type.ALERT,
                baseDay.plusDays(1).atStartOfDay());
        if (alerts > 0) {
            body.append("\nAlertes déclenchées : ").append(alerts).append('\n');
        }

        String title = (days == 1 ? "Ton bilan du jour : " : "Ton bilan de la semaine : ")
                + Formats.eur(valuation.getTotalValueEur()) + " (" + Formats.signedEur(totalDelta) + ")";
        return new ReportPreview(title, body.toString().trim());
    }

    /** Positions ouvertes classées par variation de cours sur la période (données en base, sans réseau). */
    private List<String> movers(ValuationResult valuation, LocalDate today, LocalDate baseDay) {
        record Move(String label, BigDecimal pct) {
        }
        List<Move> moves = new ArrayList<>();
        // Nom de l'actif plutôt que son symbole Yahoo (« Fetch.ai », pas « FET-EUR »)
        Map<String, String> names = new java.util.LinkedHashMap<>();
        valuation.getPortfolios().stream()
                .flatMap(p -> p.getPositions().stream())
                .filter(pos -> pos.getQuantity().signum() > 0 && !pos.isPriceMissing())
                .forEach(pos -> names.putIfAbsent(pos.getSymbol(), pos.getName() != null ? pos.getName() : pos.getSymbol()));
        names.keySet()
                .forEach(symbol -> {
                    Optional<DailyPrice> now = priceHistoryService.findOnOrBefore(symbol, today);
                    Optional<DailyPrice> base = priceHistoryService.findOnOrBefore(symbol, baseDay);
                    if (now.isPresent() && base.isPresent() && base.get().price().signum() > 0
                            && !now.get().date().equals(base.get().date())) {
                        BigDecimal pct = now.get().price().subtract(base.get().price()).multiply(BigDecimal.valueOf(100))
                                .divide(base.get().price(), 4, RoundingMode.HALF_UP);
                        moves.add(new Move(names.get(symbol), pct));
                    }
                });
        List<String> result = new ArrayList<>();
        moves.stream().filter(m -> m.pct().signum() > 0)
                .sorted(Comparator.comparing(Move::pct).reversed()).limit(MOVERS)
                .forEach(m -> result.add("▲ " + m.label() + " " + Formats.signedPercent(m.pct())));
        moves.stream().filter(m -> m.pct().signum() < 0)
                .sorted(Comparator.comparing(Move::pct)).limit(MOVERS)
                .forEach(m -> result.add("▼ " + m.label() + " " + Formats.signedPercent(m.pct())));
        return result;
    }
}

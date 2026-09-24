package com.portfolio.tracker.notification;

import com.portfolio.tracker.notification.report.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Rapports périodiques (chaque heure, pour ceux dont c'est l'heure d'envoi)
 * et purge de la boîte de réception. Les alertes sont évaluées juste après la
 * mise à jour horaire des cours ({@code MarketDataJobs}). Désactivé en test.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationJobs {

    /** Les notifications de plus de 6 mois sont supprimées. */
    private static final int RETENTION_DAYS = 180;

    private final ReportService reportService;
    private final NotificationService notificationService;

    /** Un quart d'heure après la mise à jour des cours et des snapshots. */
    @Scheduled(cron = "${reports.cron:0 15 * * * *}", zone = "Europe/Paris")
    public void sendReports() {
        int sent = reportService.sendDueReports();
        if (sent > 0) {
            log.info("Rapports envoyés : {}", sent);
        }
    }

    @Scheduled(cron = "${notifications.purge.cron:0 45 4 * * *}")
    public void purge() {
        int deleted = notificationService.purgeOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("Notifications purgées : {}", deleted);
        }
    }
}

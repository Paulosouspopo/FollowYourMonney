package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.notification.alert.AlertEvaluator;
import com.portfolio.tracker.assetprice.AssetPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Tâches de fond sur les données de marché et l'historique.
 *
 * Le backend tourne en local et s'arrête souvent : chaque démarrage (et
 * chaque fin de journée) rattrape les jours manquants, prix ET snapshots.
 * Désactivé en test via {@code app.scheduling.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataJobs {

    private final AssetPriceService assetPriceService;
    private final PortfolioHistoryService historyService;
    private final AlertEvaluator alertEvaluator;

    /** Rattrape la période pendant laquelle le backend était éteint. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        historyService.catchUp();
    }

    /** Cotations du jour, puis mise à jour du point du jour sur les courbes. */
    @Scheduled(cron = "${asset.price.update.cron:0 0 * * * *}")
    public void refreshTodayPrices() {
        assetPriceService.updateAllAssetPrices();
        historyService.rebuildAll(LocalDate.now());
        // Cours et point du jour à jour : les alertes voient les derniers chiffres
        alertEvaluator.evaluateAll();
    }

    /** Consolidation quotidienne : clôtures officielles de la veille + snapshots. */
    @Scheduled(cron = "${portfolio.history.catchup.cron:0 30 23 * * *}")
    public void nightlyCatchUp() {
        historyService.catchUp();
    }
}

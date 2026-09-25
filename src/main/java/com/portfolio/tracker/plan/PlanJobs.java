package com.portfolio.tracker.plan;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Exécution des investissements programmés : au démarrage (rattrapage des
 * jours où le backend était éteint) puis chaque soir, après la clôture des
 * marchés européens. Désactivé en test.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PlanJobs {

    private final PlanExecutor executor;

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        executor.runDuePlans();
    }

    @Scheduled(cron = "${plans.execution.cron:0 30 21 * * *}")
    public void nightly() {
        executor.runDuePlans();
    }
}

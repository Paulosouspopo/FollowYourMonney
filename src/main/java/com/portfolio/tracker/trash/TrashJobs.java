package com.portfolio.tracker.trash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Purge quotidienne de la corbeille (plus de 30 jours). */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TrashJobs {

    private final TrashService trashService;

    @Scheduled(cron = "${trash.purge.cron:0 30 4 * * *}")
    public void purge() {
        log.info("Corbeille : {} élément(s) de plus de 30 jours supprimé(s)", trashService.purge());
    }
}

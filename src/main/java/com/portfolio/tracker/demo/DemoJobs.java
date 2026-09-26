package com.portfolio.tracker.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chaque heure : suppression des comptes invités de plus de 24 h. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DemoJobs {

    private final DemoService demoService;

    @Scheduled(cron = "${demo.purge.cron:0 40 * * * *}")
    public void purge() {
        int n = demoService.purgeExpired();
        if (n > 0) {
            log.info("Démo : {} compte(s) invité(s) supprimé(s)", n);
        }
    }
}

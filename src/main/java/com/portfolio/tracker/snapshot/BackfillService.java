package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reconstruit l'historique des snapshots à partir des AssetPrice déjà en
 * base. Utile une seule fois au déploiement, ou pour un portefeuille
 * fraîchement créé qui a des transactions anciennes.
 *
 * Ne remonte que jusqu'à la date de la première transaction du portefeuille —
 * inutile de générer des snapshots à 0 avant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackfillService {

    private final PortfolioRepository portfolioRepository;
    private final SnapshotWriter snapshotWriter;

    @Async
    public void backfillAll(int daysBack) {
        log.info("===== Backfill historique — {} jours =====", daysBack);
        List<Portfolio> portfolios = portfolioRepository.findAll();

        for (Portfolio portfolio : portfolios) {
            try {
                backfillPortfolio(portfolio, daysBack);
            } catch (Exception e) {
                log.error("Backfill échoué pour le portefeuille {} : {}",
                        portfolio.getId(), e.getMessage(), e);
            }
        }
        log.info("===== Backfill terminé =====");
    }

    public void backfillPortfolio(Portfolio portfolio, int daysBack) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(daysBack);

        int written = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            LocalDateTime asOf = day.atTime(23, 59, 59);
            snapshotWriter.writeAsOf(portfolio, day, asOf);
            written++;
        }
        log.info("Backfill portefeuille {} : {} jours écrits", portfolio.getId(), written);
    }
}
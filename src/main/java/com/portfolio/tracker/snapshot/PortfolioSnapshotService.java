package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioSnapshotService {

    private final PortfolioRepository portfolioRepository;
    private final SnapshotWriter snapshotWriter;

    @Scheduled(cron = "0 30 23 * * *")
    public void createDailySnapshots() {
        LocalDate today = LocalDate.now();
        log.info("===== Snapshots quotidiens — {} =====", today);

        List<Portfolio> portfolios = portfolioRepository.findAll();
        int ok = 0, failed = 0;

        for (Portfolio portfolio : portfolios) {
            try {
                snapshotWriter.write(portfolio, today); // appel via proxy → REQUIRES_NEW actif
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("Snapshot échoué pour le portefeuille {} : {}",
                        portfolio.getId(), e.getMessage(), e);
            }
        }

        log.info("Snapshots terminés — {} réussis, {} en échec", ok, failed);
    }
}
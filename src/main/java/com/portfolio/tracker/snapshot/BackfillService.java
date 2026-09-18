package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackfillService {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final SnapshotWriter snapshotWriter;

    @Async
    public void backfillAll(int daysBack) {
        log.info("===== Backfill historique — {} jours =====", daysBack);
        for (Portfolio portfolio : portfolioRepository.findAll()) {
            try {
                backfillPortfolio(portfolio, daysBack);
            } catch (Exception e) {
                log.error("Backfill échoué pour le portefeuille {} : {}",
                        portfolio.getId(), e.getMessage(), e);
            }
        }
        log.info("===== Backfill terminé =====");
    }

    /** Backfill ciblé sur un utilisateur — c'est ce que tes tests appellent. */
    public void backfillForUser(UUID userId, int daysBack) {
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);
        log.info("Backfill utilisateur {} — {} portefeuille(s), {} jours",
                userId, portfolios.size(), daysBack);

        for (Portfolio portfolio : portfolios) {
            backfillPortfolio(portfolio, daysBack);
        }
    }

    public void backfillPortfolio(Portfolio portfolio, int daysBack) {
        LocalDate today = LocalDate.now();
        LocalDate demande = today.minusDays(daysBack);

        // On ne remonte jamais avant la première transaction :
        // c'est ce que vérifie pasDeSnapshotAvantLaPremiereTransaction()
        LocalDate premiere = transactionRepository
                .findFirstTransactionDate(portfolio.getId())
                .map(LocalDateTime::toLocalDate)
                .orElse(null);

        if (premiere == null) {
            log.debug("Portefeuille {} sans transaction — rien à backfiller", portfolio.getId());
            return;
        }

        LocalDate start = premiere.isAfter(demande) ? premiere : demande;

        int written = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            snapshotWriter.writeAsOf(portfolio, day, day.atTime(23, 59, 59));
            written++;
        }
        log.info("Backfill portefeuille {} : {} jours traités (depuis {})",
                portfolio.getId(), written, start);
    }
}
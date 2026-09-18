package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.shared.MoneyConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SnapshotWriter {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioValuationService valuationService;

    /**
     * Écrit (ou met à jour) le snapshot du jour pour un portefeuille.
     * REQUIRES_NEW : l'échec d'un portefeuille n'annule pas les autres
     * dans une boucle de scheduler.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Portfolio portfolio, LocalDate date) {
        writeAsOf(portfolio, date, date.atTime(23, 59, 59));
    }

    /**
     * Variante utilisée par le backfill : valorise à une date passée précise
     * en utilisant les AssetPrice historiques disponibles à cet instant.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAsOf(Portfolio portfolio, LocalDate date, LocalDateTime asOf) {

        if (portfolio.getUser() == null) {
            log.error("Portefeuille {} sans utilisateur, snapshot ignoré", portfolio.getId());
            return;
        }

        ValuationResult result = valuationService.valuate(
                portfolio.getUser().getId(), portfolio.getId(), asOf);

        if (result.getPortfolios().isEmpty()) {
            log.debug("Portefeuille {} vide à la date {}, snapshot ignoré", portfolio.getId(), date);
            return;
        }

        PortfolioValuation v = result.getPortfolios().get(0);

        // Position détenue mais aucun prix connu à cette date : on n'écrit pas.
        // Un snapshot à 0 créerait un faux décrochage sur la courbe.
        boolean positionDetenue = v.getPositions().stream()
                .anyMatch(p -> p.getQuantity().signum() > 0);
        boolean tousPrixManquants = v.getPositions().stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .allMatch(PositionValuation::isPriceMissing);

        if (positionDetenue && tousPrixManquants) {
            log.debug("Tous les prix manquants pour {} au {}, snapshot ignoré", portfolio.getId(), date);
            return;
        }

        PortfolioSnapshot snapshot = snapshotRepository
                .findByPortfolioIdAndSnapshotDate(portfolio.getId(), date)
                .orElseGet(() -> PortfolioSnapshot.builder()
                        .portfolio(portfolio)
                        .snapshotDate(date)
                        .build());

        snapshot.setTotalValue(v.getCurrentValueEur());
        snapshot.setTotalInvested(v.getInvestedEur());
        snapshot.setGainLoss(v.getUnrealizedGainEur());
        snapshot.setGainLossPercentage(v.getUnrealizedGainPercentage());
        snapshot.setBaseCurrency(MoneyConstants.BASE_CURRENCY);

        snapshotRepository.save(snapshot);
    }
}
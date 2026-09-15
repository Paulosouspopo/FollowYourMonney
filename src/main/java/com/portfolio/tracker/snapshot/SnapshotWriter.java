package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioSnapshotDTO;
import com.portfolio.tracker.portfolio.Portfolio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SnapshotWriter {

    private static final String DEFAULT_CURRENCY = "EUR";

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioValuationService valuationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Portfolio portfolio, LocalDate date) {
        String baseCurrency = Optional.ofNullable(portfolio.getUser().getPreferredCurrency())
                .filter(c -> !c.isBlank())
                .orElse(DEFAULT_CURRENCY);

        PortfolioSnapshotDTO valuation = valuationService.valuate(portfolio, baseCurrency);

        PortfolioSnapshot snapshot = snapshotRepository
                .findByPortfolioIdAndSnapshotDate(portfolio.getId(), date)
                .orElseGet(() -> PortfolioSnapshot.builder()
                        .portfolio(portfolio)
                        .snapshotDate(date)
                        .build());

        snapshot.setTotalValue(valuation.getCurrentValue());
        snapshot.setTotalInvested(valuation.getInvestedAmount());
        snapshot.setGainLoss(valuation.getGainLoss());
        snapshot.setGainLossPercentage(valuation.getGainLossPercentage());
        snapshot.setBaseCurrency(baseCurrency);

        snapshotRepository.save(snapshot);
    }
}
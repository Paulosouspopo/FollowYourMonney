package com.portfolio.tracker.cash;

import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intérêts estimés d'une année pour un livret (quinzaines) ou le fonds euros
 * d'une assurance-vie / d'un PER (prorata journalier), au taux du portefeuille.
 * Sert à pré-remplir le mouvement « Intérêts » que l'utilisateur valide.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestEstimateService {

    private final PortfolioRepository portfolioRepository;
    private final CashMovementRepository movementRepository;
    private final TransactionRepository transactionRepository;

    /**
     * @param estimatedEur intérêts de l'année (jusqu'à aujourd'hui pour l'année en cours)
     * @param fullYear     l'année est terminée : montant à créditer au 31/12
     * @param creditedEur  intérêts déjà saisis cette année-là
     */
    public record InterestEstimate(int year, BigDecimal rate, InterestEstimator.Method method,
            BigDecimal estimatedEur, boolean fullYear, BigDecimal creditedEur) {
    }

    public InterestEstimate estimate(UUID portfolioId, int year, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
        boolean livret = PortfolioRules.holdsOnlyCash(portfolio.getType());
        if (!livret && !PortfolioRules.isSavingsWrapper(portfolio.getType())) {
            throw new BadRequestException("Estimation réservée aux livrets et aux fonds euros");
        }
        LocalDate today = LocalDate.now();
        if (year > today.getYear()) {
            throw new BadRequestException("Année à venir");
        }

        List<CashMovement> movements = movementRepository.findAllByPortfolioIdForHistory(portfolioId);
        List<InterestEstimator.Flow> flows = new ArrayList<>();
        BigDecimal credited = BigDecimal.ZERO;
        for (CashMovement m : movements) {
            if (m.getType() == CashMovementType.INTEREST && m.getMovementDate().getYear() == year) {
                credited = credited.add(m.amountEur());
                continue; // les intérêts de l'année ne rapportent pas dans l'année
            }
            // Solde rémunéré en euros ; les changes et devises n'y entrent pas
            if (m.getType() != CashMovementType.CONVERSION
                    && MoneyConstants.BASE_CURRENCY.equals(m.getCurrency())) {
                flows.add(new InterestEstimator.Flow(m.getMovementDate(), m.signedAmount()));
            }
        }
        // Fonds euros : un achat d'unités de compte en sort, une vente y revient
        if (!livret) {
            for (Transaction tx : transactionRepository.findAllByPortfolioIdForHistory(portfolioId)) {
                if (portfolio.isMultiCurrencyCash() && !MoneyConstants.BASE_CURRENCY.equals(tx.getCurrency())) {
                    continue;
                }
                BigDecimal amount = nz(tx.getTotalAmountEur());
                BigDecimal fees = nz(tx.getFeesEur());
                BigDecimal flow = switch (tx.getType()) {
                    case BUY -> amount.add(fees).negate();
                    case SELL, DIVIDEND -> amount.subtract(fees);
                };
                flows.add(new InterestEstimator.Flow(tx.getTransactionDate().toLocalDate(), flow));
            }
        }

        InterestEstimator.Method method = livret ? InterestEstimator.Method.QUINZAINE : InterestEstimator.Method.DAILY;
        LocalDate until = year < today.getYear() ? LocalDate.of(year, 12, 31) : today;
        BigDecimal estimated = InterestEstimator.estimate(flows, portfolio.getAnnualInterestRate(), year, until, method);
        return new InterestEstimate(year, portfolio.getAnnualInterestRate(), method, estimated,
                year < today.getYear(), credited.setScale(2));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

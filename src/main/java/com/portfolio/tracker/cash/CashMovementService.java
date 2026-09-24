package com.portfolio.tracker.cash;

import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.cash.dto.CashMovementResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PortfolioHistoryChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mouvements d'argent d'un portefeuille. Chaque modification relance le
 * recalcul de l'historique à partir de la date concernée (comme une
 * transaction).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashMovementService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CashMovementRepository movementRepository;
    private final PortfolioRepository portfolioRepository;
    private final CashMovementMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public List<CashMovementResponse> findByPortfolio(UUID portfolioId, UUID userId) {
        getPortfolio(portfolioId, userId);
        return movementRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public CashMovementResponse create(UUID portfolioId, CashMovementRequest request, UUID userId) {
        Portfolio portfolio = getPortfolio(portfolioId, userId);
        if (!portfolio.isCashTracking()) {
            throw new BadRequestException(
                    "Active le suivi des liquidités de ce portefeuille pour saisir des versements et retraits");
        }
        CashMovement movement = CashMovement.builder().portfolio(portfolio).build();
        apply(movement, request);

        List<CashMovement> all = new ArrayList<>(movementRepository.findAllByPortfolioIdForHistory(portfolioId));
        all.add(movement);
        checkBalance(portfolio, all);

        CashMovement saved = movementRepository.save(movement);
        eventPublisher.publishEvent(new PortfolioHistoryChangedEvent(portfolioId, saved.getMovementDate()));
        return mapper.toResponse(saved);
    }

    @Transactional
    public CashMovementResponse update(UUID portfolioId, UUID movementId, CashMovementRequest request, UUID userId) {
        CashMovement movement = getMovement(portfolioId, movementId, userId);
        LocalDate previousDate = movement.getMovementDate();
        apply(movement, request);

        // `movement` est l'instance gérée : la liste la contient déjà modifiée
        checkBalance(movement.getPortfolio(), movementRepository.findAllByPortfolioIdForHistory(portfolioId));

        CashMovement saved = movementRepository.save(movement);
        LocalDate from = previousDate.isBefore(saved.getMovementDate()) ? previousDate : saved.getMovementDate();
        eventPublisher.publishEvent(new PortfolioHistoryChangedEvent(portfolioId, from));
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(UUID portfolioId, UUID movementId, UUID userId) {
        CashMovement movement = getMovement(portfolioId, movementId, userId);
        // Supprimer un versement ne doit pas rendre un retrait ultérieur impossible
        checkBalance(movement.getPortfolio(), movementRepository.findAllByPortfolioIdForHistory(portfolioId).stream()
                .filter(m -> !m.getId().equals(movementId))
                .toList());
        movementRepository.delete(movement);
        eventPublisher.publishEvent(new PortfolioHistoryChangedEvent(portfolioId, movement.getMovementDate()));
    }

    // ------------------------------------------------------------------ règles

    /**
     * Un livret ne peut jamais être à découvert. Un compte-titres peut l'être
     * (achats saisis avant le versement correspondant) : la valorisation
     * affiche alors un solde négatif.
     */
    private void checkBalance(Portfolio portfolio, List<CashMovement> movements) {
        if (!PortfolioRules.holdsOnlyCash(portfolio.getType())) {
            return;
        }
        BigDecimal balance = BigDecimal.ZERO;
        for (CashMovement m : movements.stream().sorted(CashMovement.CHRONOLOGICAL).toList()) {
            BigDecimal after = balance.add(m.signedAmount());
            if (after.signum() < 0) {
                throw new BadRequestException(String.format(
                        "Solde insuffisant le %s : %s € disponibles pour un débit de %s €",
                        m.getMovementDate().format(DAY), balance.toPlainString(), m.getAmount().toPlainString()));
            }
            balance = after;
        }
    }

    private void apply(CashMovement movement, CashMovementRequest request) {
        movement.setType(request.type());
        movement.setAmount(request.amount());
        movement.setMovementDate(request.movementDate());
        movement.setNotes(request.notes());
    }

    private Portfolio getPortfolio(UUID portfolioId, UUID userId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
    }

    private CashMovement getMovement(UUID portfolioId, UUID movementId, UUID userId) {
        return movementRepository.findByIdAndUserId(movementId, userId)
                .filter(m -> m.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResourceNotFoundException("Mouvement non accessible"));
    }
}

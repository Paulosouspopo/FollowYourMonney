package com.portfolio.tracker.trash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioService;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Corbeille : liste, restauration (en repassant par les services métier, donc
 * mêmes règles qu'une saisie), suppression définitive. 30 jours de rétention.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrashService {

    static final int RETENTION_DAYS = 30;

    private final TrashItemRepository repository;
    private final TransactionService transactionService;
    private final CashMovementService cashMovementService;
    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final ObjectMapper json;

    public record TrashItemResponse(UUID id, TrashItem.Kind kind, UUID portfolioId, String portfolioName, String label,
                                    LocalDateTime deletedAt, LocalDateTime expiresAt) {
    }

    public List<TrashItemResponse> list(UUID userId) {
        return repository.findByUserIdAndDeletedAtAfterOrderByDeletedAtDesc(userId,
                        LocalDateTime.now().minusDays(RETENTION_DAYS)).stream()
                .map(i -> new TrashItemResponse(i.getId(), i.getKind(), i.getPortfolioId(),
                        i.getKind() == TrashItem.Kind.PORTFOLIO ? null : portfolioRepository.findByIdAndUserId(i.getPortfolioId(), userId)
                                .map(Portfolio::getName).orElse(null),
                        i.getLabel(), i.getDeletedAt(), i.getDeletedAt().plusDays(RETENTION_DAYS)))
                .toList();
    }

    /**
     * Recrée l'élément. Une seule transaction : un seul recalcul d'historique,
     * et rien n'est recréé si une règle s'y oppose.
     *
     * @return le portefeuille concerné (pour y retourner)
     */
    @Transactional
    public UUID restore(UUID id, UUID userId) {
        TrashItem item = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Élément introuvable dans la corbeille"));
        try {
            UUID portfolioId = switch (item.getKind()) {
                case TRANSACTION -> {
                    requirePortfolio(item.getPortfolioId(), userId);
                    restoreTx(item.getPortfolioId(), json.readValue(item.getPayload(), TrashRecorder.TxPayload.class), userId);
                    yield item.getPortfolioId();
                }
                case CASH_MOVEMENT -> {
                    requirePortfolio(item.getPortfolioId(), userId);
                    restoreMovement(item.getPortfolioId(),
                            json.readValue(item.getPayload(), TrashRecorder.MovementPayload.class), userId);
                    yield item.getPortfolioId();
                }
                case PORTFOLIO -> restorePortfolio(json.readValue(item.getPayload(), TrashRecorder.PortfolioPayload.class), userId);
            };
            repository.delete(item);
            return portfolioId;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Corbeille illisible", e);
        }
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Élément introuvable dans la corbeille")));
    }

    @Transactional
    public void empty(UUID userId) {
        repository.deleteAllByUserId(userId);
    }

    @Transactional
    public int purge() {
        return repository.purgeBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
    }

    // ---------------------------------------------------------------- restauration

    private UUID restorePortfolio(TrashRecorder.PortfolioPayload p, UUID userId) {
        String name = p.name();
        List<String> taken = portfolioRepository.findByUserId(userId).stream().map(Portfolio::getName).toList();
        if (taken.contains(name)) {
            name = name + " (restauré)";
        }
        UUID id = portfolioService.create(new PortfolioCreateRequest(name, p.description(), p.type(), p.cashTracking(),
                p.annualInterestRate(), p.openedAt(), p.multiCurrencyCash()), userId).id();
        // Mouvements d'abord (un livret ne peut jamais être à découvert), puis les opérations
        p.movements().forEach(m -> restoreMovement(id, m, userId));
        p.transactions().forEach(t -> restoreTx(id, t, userId));
        return id;
    }

    private void restoreTx(UUID portfolioId, TrashRecorder.TxPayload t, UUID userId) {
        if (t.manual()) {
            // Actif non coté : on le recrée avec le même symbole, ses valeurs saisies le retrouvent
            Portfolio portfolio = requirePortfolio(portfolioId, userId);
            if (assetRepository.findBySymbolAndPortfolioIdAndUserId(t.symbol(), portfolioId, userId).isEmpty()) {
                assetRepository.save(Asset.builder().portfolio(portfolio).symbol(t.symbol()).name(t.assetName())
                        .longName(t.assetName()).assetType(t.assetType()).currency(t.assetCurrency()).manual(true).build());
            }
        }
        transactionService.create(portfolioId, new TransactionCreateRequest(t.symbol(), t.type(), t.quantity(),
                t.pricePerUnit(), t.fees(), t.currency(), t.transactionDate(), t.notes()), userId, t.externalRef());
    }

    private void restoreMovement(UUID portfolioId, TrashRecorder.MovementPayload m, UUID userId) {
        cashMovementService.create(portfolioId, new CashMovementRequest(m.type(), m.amount(), m.movementDate(), m.notes(),
                m.currency(), m.counterAmount(), m.counterCurrency()), userId, m.externalRef());
    }

    private Portfolio requirePortfolio(UUID portfolioId, UUID userId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new BadRequestException(
                        "Son portefeuille a été supprimé : restaure d'abord le portefeuille"));
    }
}

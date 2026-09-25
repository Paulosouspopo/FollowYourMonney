package com.portfolio.tracker.asset;

import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.imports.ImportAssetMappingRepository;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.YahooFinanceClient;
import com.portfolio.tracker.plan.InvestmentPlanRepository;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PortfolioHistoryChangedEvent;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Remplacer l'actif d'une ligne (mauvais choix dans la recherche : Ferrari
 * cotée à New York au lieu de Milan dans un PEA…). Les opérations sont
 * conservées telles quelles (quantités, prix, devise, dates) ; seul l'actif
 * de référence change, donc les cours utilisés pour la valorisation.
 *
 * Si le nouvel actif est déjà présent dans le portefeuille, les deux lignes
 * sont fusionnées (refusé si la fusion créait une vente à découvert). Les
 * investissements programmés et la mémoire des imports suivent.
 */
@Service
@RequiredArgsConstructor
public class AssetReplacementService {

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final InvestmentPlanRepository planRepository;
    private final ImportAssetMappingRepository importMappingRepository;
    private final MarketDataProvider marketDataProvider;
    private final AssetMapper assetMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AssetResponse replace(UUID portfolioId, UUID assetId, String rawSymbol, UUID userId) {
        Asset asset = assetRepository.findByIdAndUserId(assetId, userId)
                .filter(a -> a.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResourceNotFoundException("Actif non accessible"));
        String requested = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase();
        if (requested.isEmpty()) {
            throw new BadRequestException("Choisis le nouvel actif");
        }
        MarketQuote quote = marketDataProvider.getQuote(requested)
                .orElseThrow(() -> new BadRequestException("Actif introuvable : " + requested));
        String oldSymbol = asset.getSymbol();
        String newSymbol = quote.symbol();
        if (newSymbol.equals(oldSymbol)) {
            return assetMapper.toResponse(asset);
        }

        Optional<Asset> existing = assetRepository.findBySymbolAndPortfolioIdAndUserId(newSymbol, portfolioId, userId);
        Asset target;
        if (existing.isPresent()) {
            target = existing.get();
            checkNoShortSale(assetId, target.getId(), userId);
            transactionRepository.moveToAsset(assetId, target.getId());
            assetRepository.delete(assetRepository.getReferenceById(assetId));
        } else {
            target = asset;
            asset.setSymbol(newSymbol);
            asset.setName(quote.longName() != null ? quote.longName() : newSymbol);
            asset.setLongName(quote.longName());
            asset.setExchangeName(quote.exchangeName());
            asset.setCurrency(quote.currency());
            asset.setAssetType(YahooFinanceClient.mapAssetType(quote.instrumentType()));
        }

        // Les plans et la mémoire d'import ne doivent plus ramener l'ancien actif
        planRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .filter(p -> oldSymbol.equals(p.getSymbol()))
                .forEach(p -> p.setSymbol(newSymbol));
        importMappingRepository.findByUserId(userId).stream()
                .filter(m -> oldSymbol.equals(m.getSymbol()))
                .forEach(m -> m.setSymbol(newSymbol));

        eventPublisher.publishEvent(PortfolioHistoryChangedEvent.full(portfolioId));
        return assetMapper.toResponse(target);
    }

    /** Rejoue les opérations fusionnées : à aucun moment la quantité détenue ne doit être négative. */
    private void checkNoShortSale(UUID sourceId, UUID targetId, UUID userId) {
        List<Transaction> all = new ArrayList<>(transactionRepository.findByAssetIdAndUserId(sourceId, userId));
        all.addAll(transactionRepository.findByAssetIdAndUserId(targetId, userId));
        all.sort(Transaction.CHRONOLOGICAL);
        BigDecimal held = BigDecimal.ZERO;
        for (Transaction t : all) {
            if (t.getType() == TransactionType.BUY) {
                held = held.add(t.getQuantity());
            } else if (t.getType() == TransactionType.SELL) {
                held = held.subtract(t.getQuantity());
                if (held.compareTo(new BigDecimal("-0.00000001")) < 0) {
                    throw new BadRequestException("Fusion impossible : une vente dépasserait la quantité détenue");
                }
            }
        }
    }
}

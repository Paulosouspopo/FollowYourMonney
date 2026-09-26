package com.portfolio.tracker.asset.manual;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetMapper;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.asset.ManualAssets;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.assetprice.AssetPrice;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PortfolioHistoryChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Actifs non cotés (fonds absent de Yahoo, FCPE d'épargne salariale…) : créés
 * avec un symbole interne, valorisés par les valeurs que l'utilisateur saisit.
 * Ces valeurs sont des lignes d'asset_prices (source MANUAL) : valorisation,
 * historique et repli « dernier cours connu » fonctionnent sans cas particulier.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManualAssetService {

    static final String SOURCE = "MANUAL";

    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetPriceRepository priceRepository;
    private final AssetMapper assetMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** Actifs non cotés du portefeuille (pour les retrouver dans la recherche). */
    public List<AssetResponse> list(UUID portfolioId, UUID userId) {
        portfolio(portfolioId, userId);
        return assetRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .filter(Asset::isManual)
                .sorted(Comparator.comparing(Asset::getName, String.CASE_INSENSITIVE_ORDER))
                .map(assetMapper::toResponse)
                .toList();
    }

    @Transactional
    public AssetResponse create(UUID portfolioId, ManualAssetRequest request, UUID userId) {
        Portfolio portfolio = portfolio(portfolioId, userId);
        if (PortfolioRules.holdsOnlyCash(portfolio.getType())) {
            throw new BadRequestException("Un livret ne détient pas d'actifs");
        }
        String name = request.name().trim();
        Asset asset = Asset.builder()
                .portfolio(portfolio)
                .symbol(ManualAssets.newSymbol())
                .name(name)
                .longName(name)
                .assetType(request.assetType())
                .currency(request.currency() != null ? request.currency() : MoneyConstants.BASE_CURRENCY)
                .manual(true)
                .build();
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    public List<ValuationResponse> valuations(UUID portfolioId, UUID assetId, UUID userId) {
        Asset asset = manualAsset(portfolioId, assetId, userId);
        return priceRepository.findLatestPricesBySymbol(asset.getSymbol(), 1000).stream()
                .map(p -> new ValuationResponse(p.getPriceDate(), p.getPrice(), p.getCurrency()))
                .toList();
    }

    /** Ajoute ou remplace la valeur d'un jour, puis recalcule l'historique depuis ce jour. */
    @Transactional
    public ValuationResponse saveValuation(UUID portfolioId, UUID assetId, ValuationRequest request, UUID userId) {
        Asset asset = manualAsset(portfolioId, assetId, userId);
        AssetPrice price = priceRepository.findBySymbolAndPriceDate(asset.getSymbol(), request.date())
                .orElseGet(() -> AssetPrice.builder().symbol(asset.getSymbol()).priceDate(request.date()).build());
        price.setPrice(request.price());
        price.setCurrency(asset.getCurrency());
        // « Mis à jour le » = date du relevé, pas celle de la saisie
        price.setLastUpdated(request.date().atTime(18, 0));
        price.setSource(SOURCE);
        priceRepository.save(price);
        eventPublisher.publishEvent(new PortfolioHistoryChangedEvent(portfolioId, request.date()));
        return new ValuationResponse(price.getPriceDate(), price.getPrice(), price.getCurrency());
    }

    @Transactional
    public void deleteValuation(UUID portfolioId, UUID assetId, LocalDate date, UUID userId) {
        Asset asset = manualAsset(portfolioId, assetId, userId);
        AssetPrice price = priceRepository.findBySymbolAndPriceDate(asset.getSymbol(), date)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune valeur saisie ce jour-là"));
        priceRepository.delete(price);
        eventPublisher.publishEvent(new PortfolioHistoryChangedEvent(portfolioId, date));
    }

    private Asset manualAsset(UUID portfolioId, UUID assetId, UUID userId) {
        Asset asset = assetRepository.findByIdAndUserId(assetId, userId)
                .filter(a -> a.getPortfolio().getId().equals(portfolioId))
                .orElseThrow(() -> new ResourceNotFoundException("Actif non accessible"));
        if (!asset.isManual()) {
            throw new BadRequestException("Cet actif est coté : sa valeur vient du marché");
        }
        return asset;
    }

    private Portfolio portfolio(UUID portfolioId, UUID userId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
    }
}

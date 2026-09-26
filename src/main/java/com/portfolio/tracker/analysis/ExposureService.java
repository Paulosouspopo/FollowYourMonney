package com.portfolio.tracker.analysis;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.marketdata.AssetProfile;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Radiographie du patrimoine (ou d'un portefeuille) : positions valorisées +
 * profils de marché en cache (téléchargés au besoin : la première ouverture
 * peut prendre quelques secondes).
 */
@Service
@RequiredArgsConstructor
public class ExposureService {

    private final PortfolioValuationService valuationService;
    private final AssetProfileService profileService;
    private final AssetRepository assetRepository;

    public ExposureCalculator.Exposure exposure(UUID userId, UUID portfolioId) {
        ValuationResult valuation = valuationService.valuate(userId, portfolioId, null);
        List<PortfolioValuation> portfolios = valuation.getPortfolios();

        Map<UUID, Asset> assets = assetRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        List<ExposureCalculator.Line> lines = new ArrayList<>();
        List<ExposureCalculator.Cash> cash = new ArrayList<>();
        for (PortfolioValuation p : portfolios) {
            for (PositionValuation pos : p.getPositions()) {
                if (pos.getQuantity() == null || pos.getQuantity().signum() <= 0) {
                    continue;
                }
                Asset asset = assets.get(pos.getAssetId());
                BigDecimal fee = asset != null ? asset.getAnnualFeePct() : null;
                lines.add(new ExposureCalculator.Line(pos.getSymbol(), pos.getName(), pos.getAssetType(),
                        asset != null && asset.getCurrency() != null ? asset.getCurrency() : pos.getPriceCurrency(),
                        pos.getCurrentValueEur().doubleValue(), fee != null ? fee.doubleValue() : null));
            }
            if (p.isCashTracking() && p.getCashEur() != null && p.getCashEur().signum() > 0) {
                String category = PortfolioRules.holdsOnlyCash(p.getType()) ? "LIVRETS"
                        : p.getType() == PortfolioType.ASSURANCE_VIE || p.getType() == PortfolioType.PER ? "FONDS_EUROS"
                                : "LIQUIDITES";
                if (p.getCashBalances() != null && !p.getCashBalances().isEmpty()) {
                    p.getCashBalances().forEach(b -> cash.add(new ExposureCalculator.Cash(category, b.currency(),
                            b.amountEur().doubleValue())));
                } else {
                    cash.add(new ExposureCalculator.Cash(category, MoneyConstants.BASE_CURRENCY, p.getCashEur().doubleValue()));
                }
            }
        }
        Map<String, AssetProfile> profiles = profileService.profiles(lines.stream()
                .filter(l -> l.type() != com.portfolio.tracker.asset.AssetType.CRYPTO)
                .map(ExposureCalculator.Line::symbol).toList());
        double brokerFees = portfolios.stream().mapToDouble(p -> p.getTotalFeesEur().doubleValue()).sum();
        return ExposureCalculator.compute(lines, cash, profiles, brokerFees);
    }

    /**
     * Frais courants annuels d'un fonds saisis par l'utilisateur, pour toutes
     * ses lignes de ce symbole (null = revenir à ceux de Yahoo).
     */
    @Transactional
    public void setAnnualFee(UUID userId, String symbol, BigDecimal feePct) {
        if (feePct != null && (feePct.signum() < 0 || feePct.compareTo(BigDecimal.TEN) > 0)) {
            throw new BadRequestException("Frais annuels entre 0 et 10 %");
        }
        List<Asset> matching = assetRepository.findByUserId(userId).stream()
                .filter(a -> a.getSymbol().equals(symbol)).toList();
        if (matching.isEmpty()) {
            throw new ResourceNotFoundException("Actif non accessible");
        }
        matching.forEach(a -> a.setAnnualFeePct(feePct));
        assetRepository.saveAll(matching);
    }
}

package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.assetprice.dto.LatestPriceProjection;
import com.portfolio.tracker.dashboard.dto.*;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Moteur de valorisation. Toute la performance de l'application repose ici.
 *
 * Principes :
 * 1. Une seule requête pour toutes les transactions concernées (JOIN FETCH),
 * une seule requête pour tous les derniers prix (DISTINCT ON). Aucun N+1.
 * 2. Le coût d'une position est calculé au CUMP (coût unitaire moyen pondéré),
 * recalculé transaction par transaction, dans l'ordre chronologique.
 * 3. L'investi (investedEur) reflète le taux de change du JOUR DE L'ACHAT
 * (figé sur la transaction). La valeur courante (currentValueEur) utilise
 * le taux du jour. C'est volontaire : le montant que j'ai sorti de ma poche
 * ne bouge pas rétroactivement parce que l'euro a fluctué depuis.
 * 4. Un prix manquant ne fait pas planter la valorisation : la position est
 * valorisée à 0 et signalée via {@code priceMissing}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioValuationService {

    private final TransactionRepository transactionRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final CurrencyConverter currencyConverter;

    /**
     * Valorise un ou tous les portefeuilles d'un utilisateur, à une date donnée.
     *
     * @param portfolioIdOrNull null = tous les portefeuilles de l'utilisateur
     * @param asOf              null = maintenant (utile pour backfill/historique)
     */
    public ValuationResult valuate(UUID userId, UUID portfolioIdOrNull, LocalDateTime asOf) {

        List<Transaction> transactions = transactionRepository.findAllForValuation(userId, portfolioIdOrNull, asOf);

        if (transactions.isEmpty()) {
            return ValuationResult.empty();
        }

        Set<String> symbols = transactions.stream()
                .map(t -> t.getAsset().getSymbol())
                .collect(Collectors.toSet());

        Map<String, PriceSnapshot> prices = loadPrices(symbols, asOf);

        // Regroupement en mémoire : plus aucune requête à partir d'ici
        Map<UUID, List<Transaction>> byPortfolio = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getAsset().getPortfolio().getId()));

        CurrencyConverter.Session fxSession = currencyConverter.openSession();

        List<PortfolioValuation> portfolios = byPortfolio.values().stream()
                .map(txs -> valuatePortfolio(txs, prices, fxSession))
                .toList();

        return ValuationResult.aggregate(portfolios);
    }

    // --------------------------------------------------------- niveau portfolio

    private PortfolioValuation valuatePortfolio(List<Transaction> portfolioTxs,
            Map<String, PriceSnapshot> prices,
            CurrencyConverter.Session fxSession) {

        Portfolio portfolio = portfolioTxs.get(0).getAsset().getPortfolio();

        Map<UUID, List<Transaction>> byAsset = portfolioTxs.stream()
                .collect(Collectors.groupingBy(t -> t.getAsset().getId()));

        List<PositionValuation> positions = byAsset.values().stream()
                .map(txs -> valuatePosition(txs, prices, fxSession))
                .filter(p -> p.getQuantity().signum() > 0
                        || p.getRealizedGainEur().signum() != 0
                        || p.getDividendsEur().signum() != 0)
                .sorted(Comparator.comparing(PositionValuation::getSymbol))
                .toList();

        BigDecimal currentValue = sum(positions, PositionValuation::getCurrentValueEur);
        BigDecimal invested = sum(positions, PositionValuation::getInvestedEur);
        BigDecimal unrealized = currentValue.subtract(invested);
        BigDecimal realized = sum(positions, PositionValuation::getRealizedGainEur);
        BigDecimal dividends = sum(positions, PositionValuation::getDividendsEur);
        BigDecimal fees = sum(positions, PositionValuation::getTotalFeesEur);

        long openCount = positions.stream().filter(p -> p.getQuantity().signum() > 0).count();

        return PortfolioValuation.builder()
                .portfolioId(portfolio.getId())
                .name(portfolio.getName())
                .type(portfolio.getType())
                .currentValueEur(currentValue)
                .investedEur(invested)
                .unrealizedGainEur(unrealized)
                .unrealizedGainPercentage(percentage(unrealized, invested))
                .realizedGainEur(realized)
                .dividendsEur(dividends)
                .totalFeesEur(fees)
                .positions(positions)
                .openPositionCount((int) openCount)
                .hasIncompletePrices(positions.stream().anyMatch(PositionValuation::isPriceMissing))
                .build();
    }

    // ---------------------------------------------------------- niveau position

    /**
     * Calcule le CUMP en rejouant les transactions dans l'ordre chronologique.
     *
     * Règles :
     * - BUY : augmente la quantité et le coût total (frais inclus dans le coût,
     * car ils font partie du prix de revient réel de la position).
     * - SELL : réduit la quantité proportionnellement au coût moyen courant.
     * Le gain réalisé = produit net de la vente - coût sorti.
     * - DIVIDEND : n'affecte ni quantité ni coût, alimente uniquement
     * dividendsEur.
     */
    private PositionValuation valuatePosition(List<Transaction> assetTxs,
            Map<String, PriceSnapshot> prices,
            CurrencyConverter.Session fxSession) {

        assetTxs.sort(Comparator.comparing(Transaction::getTransactionDate));
        Asset asset = assetTxs.get(0).getAsset();

        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal costBasisEur = BigDecimal.ZERO; // coût total encore "en jeu"
        BigDecimal realizedEur = BigDecimal.ZERO;
        BigDecimal dividendsEur = BigDecimal.ZERO;
        BigDecimal totalFeesEur = BigDecimal.ZERO;

        for (Transaction tx : assetTxs) {
            totalFeesEur = totalFeesEur.add(nz(tx.getFeesEur()));

            switch (tx.getType()) {
                case BUY -> {
                    BigDecimal txCost = nz(tx.getTotalAmountEur()).add(nz(tx.getFeesEur()));
                    quantity = quantity.add(tx.getQuantity());
                    costBasisEur = costBasisEur.add(txCost);
                }
                case SELL -> {
                    BigDecimal sellQty = tx.getQuantity().min(quantity);
                    if (quantity.signum() > 0 && sellQty.signum() > 0) {
                        BigDecimal avgCost = costBasisEur.divide(
                                quantity, MoneyConstants.QUANTITY_SCALE, MoneyConstants.ROUNDING);
                        BigDecimal costOut = avgCost.multiply(sellQty)
                                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);

                        // Produit net de la vente = ce qui a été encaissé, frais déduits
                        BigDecimal proceedsEur = nz(tx.getTotalAmountEur()).subtract(nz(tx.getFeesEur()));

                        realizedEur = realizedEur.add(proceedsEur.subtract(costOut));
                        costBasisEur = costBasisEur.subtract(costOut);
                        quantity = quantity.subtract(sellQty);
                    }
                    // Si sellQty < tx.getQuantity() : vente à découvert non supportée,
                    // on borne à la quantité détenue plutôt que de planter.
                }
                case DIVIDEND -> dividendsEur = dividendsEur.add(nz(tx.getTotalAmountEur()));
            }
        }

        // Résidu d'arrondi : si la position est totalement soldée, le coût
        // restant doit être nul, pas un epsilon négatif ou positif.
        if (quantity.signum() == 0) {
            costBasisEur = BigDecimal.ZERO;
        }

        PriceSnapshot price = prices.getOrDefault(asset.getSymbol(), PriceSnapshot.missing(asset.getSymbol()));

        BigDecimal currentValueEur;
        BigDecimal averageCostEur = quantity.signum() > 0
                ? costBasisEur.divide(quantity, MoneyConstants.QUANTITY_SCALE, MoneyConstants.ROUNDING)
                : BigDecimal.ZERO;

        // - Si quantity == 0 : position soldée, pas besoin de prix, donc priceMissing =
        // false
        // - Si quantity > 0 et prix absent : priceMissing = true
        boolean priceMissing = quantity.signum() > 0 && price.missing();

        if (priceMissing || quantity.signum() == 0) {
            currentValueEur = BigDecimal.ZERO;
        } else {
            BigDecimal priceEur = fxSession.toEur(price.price(), price.currency());
            currentValueEur = quantity.multiply(priceEur)
                    .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
        }

        BigDecimal unrealizedEur = currentValueEur.subtract(costBasisEur);

        return PositionValuation.builder()
                .assetId(asset.getId())
                .symbol(asset.getSymbol())
                .name(asset.getName())
                .assetType(asset.getAssetType())
                .quantity(quantity)
                .averageCostEur(averageCostEur)
                .investedEur(costBasisEur)
                .currentValueEur(currentValueEur)
                .unrealizedGainEur(unrealizedEur)
                .unrealizedGainPercentage(percentage(unrealizedEur, costBasisEur))
                .realizedGainEur(realizedEur)
                .dividendsEur(dividendsEur)
                .totalFeesEur(totalFeesEur)
                .lastPrice(price.price())
                .priceCurrency(price.currency())
                .priceAsOf(price.asOf())
                .priceMissing(priceMissing)
                .build();
    }

    // -------------------------------------------------------------- prix (bulk)

    private Map<String, PriceSnapshot> loadPrices(Set<String> symbols, LocalDateTime asOf) {
        List<String> symbolsList = new ArrayList<>(symbols); // Convertis en List
        List<LatestPriceProjection> rows = (asOf == null)
                ? assetPriceRepository.findLatestForSymbols(symbolsList)
                : assetPriceRepository.findLatestForSymbolsAsOf(symbolsList, asOf);

        Map<String, PriceSnapshot> result = new HashMap<>();
        for (LatestPriceProjection row : rows) {
            result.put(row.getSymbol(), new PriceSnapshot(
                    row.getSymbol(), row.getPrice(), row.getCurrency(), row.getLastUpdated(), false));
        }

        Set<String> missing = new HashSet<>(symbols);
        missing.removeAll(result.keySet());
        if (!missing.isEmpty()) {
            log.warn("Prix manquant pour les symboles : {}", missing);
            missing.forEach(s -> result.put(s, PriceSnapshot.missing(s)));
        }

        return result;
    }

    // ------------------------------------------------------------------- utils

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal sum(List<PositionValuation> list, Function<PositionValuation, BigDecimal> getter) {
        return list.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    private BigDecimal percentage(BigDecimal gain, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gain.multiply(BigDecimal.valueOf(100))
                .divide(base, MoneyConstants.PERCENT_SCALE, MoneyConstants.ROUNDING);
    }
}
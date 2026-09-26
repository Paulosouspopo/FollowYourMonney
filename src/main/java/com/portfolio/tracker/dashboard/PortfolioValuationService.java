package com.portfolio.tracker.dashboard;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.assetprice.AssetPriceRepository;
import com.portfolio.tracker.assetprice.dto.LatestPriceProjection;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.dashboard.dto.*;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PerformanceFlows;
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
 * 4. Investi = apports nets, l'argent réellement sorti de la poche
 * ({@link PerformanceFlows}) : versements - retraits (+ découvert) pour un
 * compte suivi, achats - ventes - dividendes pour un compte sans suivi. Le gain
 * (valeur - investi) inclut donc latent, réalisé, dividendes et intérêts. La
 * valeur d'un compte suivi = positions + solde positif (un découvert = des
 * versements non saisis). Le % de plus-value latente reste calculé sur le seul
 * prix de revient des positions.
 * 5. Aucun cours de marché : la position est valorisée au prix de sa
 * dernière transaction (même règle que la courbe historique, jamais 0) et
 * signalée via {@code priceMissing}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortfolioValuationService {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final CurrencyConverter currencyConverter;
    private final CashMovementRepository cashMovementRepository;

    /**
     * Valorise un ou tous les portefeuilles d'un utilisateur, à une date donnée.
     *
     * @param portfolioIdOrNull null = tous les portefeuilles de l'utilisateur
     * @param asOf              null = maintenant (utile pour backfill/historique)
     */
    public ValuationResult valuate(UUID userId, UUID portfolioIdOrNull, LocalDateTime asOf) {

        // 1. Source de vérité : les portefeuilles de l'utilisateur
        List<Portfolio> portfolios = (portfolioIdOrNull == null)
                ? portfolioRepository.findByUserId(userId)
                : portfolioRepository.findByIdAndUserId(portfolioIdOrNull, userId)
                        .map(List::of)
                        .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));

        if (portfolios.isEmpty()) {
            return ValuationResult.empty();
        }

        // 2. Transactions et mouvements d'argent (une requête chacun)
        List<Transaction> transactions = transactionRepository.findAllForValuation(userId, portfolioIdOrNull, asOf);
        Map<UUID, List<CashMovement>> movementsByPortfolio = cashMovementRepository
                .findAllForValuation(userId, portfolioIdOrNull).stream()
                .filter(m -> asOf == null || !m.getMovementDate().isAfter(asOf.toLocalDate()))
                .collect(Collectors.groupingBy(m -> m.getPortfolio().getId()));

        Set<String> symbols = transactions.stream()
                .map(t -> t.getAsset().getSymbol())
                .collect(Collectors.toSet());

        Map<String, PriceSnapshot> prices = symbols.isEmpty()
                ? Map.of()
                : loadPrices(symbols, asOf);

        Map<UUID, List<Transaction>> byPortfolio = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getAsset().getPortfolio().getId()));

        CurrencyConverter.Session fxSession = currencyConverter.openSession();

        // 3. Un PortfolioValuation par portefeuille, même vide
        List<PortfolioValuation> valuations = portfolios.stream()
                .map(p -> valuatePortfolio(p,
                        byPortfolio.getOrDefault(p.getId(), List.of()),
                        movementsByPortfolio.getOrDefault(p.getId(), List.of()),
                        prices, fxSession))
                .toList();

        return ValuationResult.aggregate(valuations);
    }

    private PortfolioValuation valuatePortfolio(Portfolio portfolio,
            List<Transaction> portfolioTxs,
            List<CashMovement> movements,
            Map<String, PriceSnapshot> prices,
            CurrencyConverter.Session fxSession) {

        Map<UUID, List<Transaction>> byAsset = portfolioTxs.stream()
                .collect(Collectors.groupingBy(t -> t.getAsset().getId()));

        List<PositionValuation> positions = byAsset.values().stream()
                .map(txs -> valuatePosition(new ArrayList<>(txs), prices, fxSession))
                .filter(p -> p.getQuantity().signum() > 0
                        || p.getRealizedGainEur().signum() != 0
                        || p.getDividendsEur().signum() != 0)
                .sorted(Comparator.comparing(PositionValuation::getSymbol))
                .toList();

        BigDecimal positionsValue = sum(positions, PositionValuation::getCurrentValueEur);
        BigDecimal positionsCost = sum(positions, PositionValuation::getInvestedEur);
        BigDecimal unrealized = positionsValue.subtract(positionsCost);
        BigDecimal fees = sum(positions, PositionValuation::getTotalFeesEur);

        // Liquidités (règles dans CashState) : ignorées si le suivi est désactivé
        CashState cash = new CashState(portfolio.isMultiCurrencyCash());
        if (portfolio.isCashTracking()) {
            movements.forEach(cash::apply);
            portfolioTxs.forEach(cash::apply);
            fees = fees.add(cash.getAccountFeesEur());
        }
        // Soldes en devises : taux du jour (comme les positions)
        BigDecimal cashEur = scale(cash.valueEur(c -> fxSession.rate(c, MoneyConstants.BASE_CURRENCY)));
        BigDecimal value = portfolio.isCashTracking()
                ? PerformanceFlows.trackedValue(positionsValue, cashEur)
                : positionsValue;
        BigDecimal invested = portfolio.isCashTracking()
                ? PerformanceFlows.contributed(cash, cashEur)
                : portfolioTxs.stream().map(PerformanceFlows::tradeFlow).reduce(BigDecimal.ZERO, BigDecimal::add);

        long openCount = positions.stream().filter(p -> p.getQuantity().signum() > 0).count();

        return PortfolioValuation.builder()
                .portfolioId(portfolio.getId())
                .name(portfolio.getName())
                .type(portfolio.getType())
                .currentValueEur(scale(value))
                .investedEur(scale(invested))
                .positionsCostEur(positionsCost)
                .unrealizedGainEur(unrealized)
                .unrealizedGainPercentage(percentage(unrealized, positionsCost))
                .realizedGainEur(sum(positions, PositionValuation::getRealizedGainEur))
                .dividendsEur(sum(positions, PositionValuation::getDividendsEur))
                .interestEur(scale(cash.getInterestEur()))
                .totalFeesEur(scale(fees))
                .cashTracking(portfolio.isCashTracking())
                .cashEur(cashEur)
                .netDepositsEur(scale(cash.getNetDepositsEur()))
                .employerContributionsEur(scale(cash.getEmployerContributionsEur()))
                .multiCurrencyCash(portfolio.isMultiCurrencyCash())
                .cashBalances(cashBalances(cash, fxSession))
                .annualInterestRate(portfolio.getAnnualInterestRate())
                .positions(positions)
                .openPositionCount((int) openCount)
                .hasIncompletePrices(positions.stream().anyMatch(PositionValuation::isPriceMissing))
                .build();
    }

    /** Détail des soldes par devise (euros d'abord) ; vide si le compte n'a que des euros. */
    private static List<CashBalance> cashBalances(CashState cash, CurrencyConverter.Session fxSession) {
        if (cash.getForeignBalances().isEmpty()) {
            return List.of();
        }
        List<CashBalance> out = new ArrayList<>();
        out.add(new CashBalance(MoneyConstants.BASE_CURRENCY, scale(cash.getBalanceEur()), scale(cash.getBalanceEur())));
        cash.getForeignBalances().forEach((currency, amount) -> out.add(new CashBalance(currency, scale(amount),
                scale(amount.multiply(fxSession.rate(currency, MoneyConstants.BASE_CURRENCY))))));
        return out;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    // ---------------------------------------------------------- niveau position

    /**
     * Calcule le CUMP en rejouant les transactions dans l'ordre chronologique
     * (règles détaillées dans {@link PositionState}).
     */
    private PositionValuation valuatePosition(List<Transaction> assetTxs,
            Map<String, PriceSnapshot> prices,
            CurrencyConverter.Session fxSession) {

        assetTxs.sort(Transaction.CHRONOLOGICAL);
        Asset asset = assetTxs.get(0).getAsset();

        PositionState state = new PositionState();
        assetTxs.forEach(state::apply);

        BigDecimal quantity = state.getQuantity();
        BigDecimal costBasisEur = state.getCostBasisEur();

        PriceSnapshot price = prices.getOrDefault(asset.getSymbol(), PriceSnapshot.missing(asset.getSymbol()));

        BigDecimal averageCostEur = state.averageCostEur();

        // Position soldée : pas besoin de prix. Position ouverte sans cours :
        // repli sur le prix de la dernière transaction, comme PortfolioHistoryService
        // (le dashboard et la courbe doivent donner le même chiffre).
        boolean priceMissing = quantity.signum() > 0 && price.missing();
        BigDecimal unitPrice = price.missing() ? state.getLastTradePrice() : price.price();
        String unitCurrency = price.missing() ? state.getLastTradeCurrency() : price.currency();

        BigDecimal currentValueEur = BigDecimal.ZERO;
        if (quantity.signum() > 0 && unitPrice != null) {
            BigDecimal priceEur = fxSession.toEur(unitPrice, unitCurrency);
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
                .realizedGainEur(state.getRealizedEur())
                .dividendsEur(state.getDividendsEur())
                .totalFeesEur(state.getFeesEur())
                .lastPrice(unitPrice)
                .priceCurrency(unitCurrency)
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
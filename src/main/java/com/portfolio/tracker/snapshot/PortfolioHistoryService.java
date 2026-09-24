package com.portfolio.tracker.snapshot;

import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.dashboard.PositionState;
import com.portfolio.tracker.exchangerate.FxSymbols;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Historique des portefeuilles : une ligne {@link PortfolioSnapshot} par jour,
 * de la première transaction à aujourd'hui.
 *
 * Deux étapes, volontairement séparées :
 * <ol>
 * <li>{@link #refresh} : s'assure que les séries de prix et de taux de change
 * nécessaires sont en base (appels HTTP, hors transaction), puis reconstruit ;</li>
 * <li>{@link #rebuild} : recalcul pur base de données + mémoire, idempotent
 * (suppression puis réinsertion de la plage), dans une seule transaction.</li>
 * </ol>
 *
 * Coût de la reconstruction : 4 requêtes par portefeuille (transactions,
 * prix, amorces, taux) quel que soit le nombre de jours, puis un parcours
 * jour par jour en mémoire en O(jours × positions + transactions). Aucune
 * requête dans la boucle.
 *
 * Toutes les écritures passent par des transactions REQUIRES_NEW : le service
 * est appelable depuis un callback afterCommit (où la transaction d'origine
 * est déjà validée) comme depuis un scheduler.
 */
@Service
@Slf4j
public class PortfolioHistoryService {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PriceHistoryService priceHistoryService;
    private final CurrencyConverter currencyConverter;
    private final TransactionTemplate txNew;

    public PortfolioHistoryService(PortfolioRepository portfolioRepository,
            TransactionRepository transactionRepository,
            PortfolioSnapshotRepository snapshotRepository,
            PriceHistoryService priceHistoryService,
            CurrencyConverter currencyConverter,
            PlatformTransactionManager transactionManager) {
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.priceHistoryService = priceHistoryService;
        this.currencyConverter = currencyConverter;
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ============================================================ orchestration

    /**
     * Complète les données de marché puis reconstruit l'historique à partir de
     * {@code from} (null = depuis la première transaction). Ne lève jamais :
     * un échec ici ne doit pas faire échouer l'opération utilisateur, déjà validée.
     */
    public void refresh(UUID portfolioId, LocalDate from) {
        try {
            Map<String, LocalDate> needs = txNew.execute(s -> marketDataNeeds(portfolioId));
            if (needs != null) {
                needs.forEach(priceHistoryService::ensureCoverage);
            }
            rebuild(portfolioId, from);
        } catch (Exception e) {
            log.error("Rafraîchissement de l'historique du portefeuille {} échoué : {}",
                    portfolioId, e.getMessage(), e);
        }
    }

    /**
     * Rattrapage (démarrage, fin de journée, endpoint admin) : complète les
     * trous de données puis recalcule chaque portefeuille depuis son dernier
     * snapshot connu — donc toute la période où le backend était éteint.
     */
    public void catchUp() {
        List<UUID> ids = portfolioRepository.findAllIds();
        log.info("===== Rattrapage de l'historique : {} portefeuille(s) =====", ids.size());
        for (UUID id : ids) {
            LocalDate last = txNew.execute(s -> snapshotRepository.findLastSnapshotDate(id).orElse(null));
            refresh(id, last);
        }
        log.info("===== Rattrapage terminé =====");
    }

    /** Recalcul complet de tous les portefeuilles depuis leur première transaction. */
    public void refreshAllFull() {
        portfolioRepository.findAllIds().forEach(id -> refresh(id, null));
    }

    /**
     * Recalcul de tous les portefeuilles depuis {@code from}, sans appel réseau.
     * Utilisé par le job horaire pour tenir le point du jour à jour.
     */
    public void rebuildAll(LocalDate from) {
        int ok = 0, failed = 0;
        for (UUID id : portfolioRepository.findAllIds()) {
            try {
                rebuild(id, from);
                ok++;
            } catch (Exception e) {
                failed++;
                log.error("Snapshot échoué pour le portefeuille {} : {}", id, e.getMessage(), e);
            }
        }
        log.info("Snapshots depuis {} : {} réussis, {} en échec", from, ok, failed);
    }

    /**
     * Symboles (actifs et paires de devises) dont le portefeuille a besoin,
     * avec la date à partir de laquelle leur historique est nécessaire.
     */
    private Map<String, LocalDate> marketDataNeeds(UUID portfolioId) {
        Map<String, LocalDate> needs = new LinkedHashMap<>();
        for (Transaction tx : transactionRepository.findAllByPortfolioIdForHistory(portfolioId)) {
            LocalDate day = tx.getTransactionDate().toLocalDate();
            needs.merge(tx.getAsset().getSymbol(), day, (a, b) -> a.isBefore(b) ? a : b);
            for (String currency : new String[] { tx.getAsset().getCurrency(), tx.getCurrency() }) {
                if (isForeign(currency)) {
                    needs.merge(FxSymbols.pair(currency, MoneyConstants.BASE_CURRENCY), day,
                            (a, b) -> a.isBefore(b) ? a : b);
                }
            }
        }
        return needs;
    }

    // ============================================================ reconstruction

    /**
     * Reconstruit les snapshots de {@code from} (null = première transaction)
     * jusqu'à aujourd'hui. Idempotent. Aucun appel réseau.
     *
     * @return nombre de snapshots écrits
     */
    public int rebuild(UUID portfolioId, LocalDate from) {
        Integer written = txNew.execute(s -> doRebuild(portfolioId, from));
        return written != null ? written : 0;
    }

    private int doRebuild(UUID portfolioId, LocalDate requestedFrom) {
        Portfolio portfolio = portfolioRepository.findByIdForUpdate(portfolioId).orElse(null);
        if (portfolio == null) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        List<Transaction> txs = transactionRepository.findAllByPortfolioIdForHistory(portfolioId);
        if (txs.isEmpty() || day(txs.get(0)).isAfter(today)) {
            snapshotRepository.deleteByPortfolioId(portfolioId);
            return 0;
        }

        LocalDate firstDay = day(txs.get(0));
        LocalDate start = requestedFrom == null || requestedFrom.isBefore(firstDay) ? firstDay : requestedFrom;
        if (start.isAfter(today)) {
            start = today;
        }

        snapshotRepository.deleteForRebuild(portfolioId, firstDay, start);

        MarketData market = loadMarketData(txs, start, today);

        Map<UUID, PositionState> states = new HashMap<>();
        Map<UUID, String> symbols = new HashMap<>();
        List<PortfolioSnapshot> snapshots = new ArrayList<>();
        int next = 0;

        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            // Rejoue toutes les transactions jusqu'à ce jour inclus (y compris
            // celles antérieures à start lors de la première itération).
            while (next < txs.size() && !day(txs.get(next)).isAfter(day)) {
                Transaction tx = txs.get(next++);
                states.computeIfAbsent(tx.getAsset().getId(), id -> new PositionState()).apply(tx);
                symbols.putIfAbsent(tx.getAsset().getId(), tx.getAsset().getSymbol());
            }
            snapshots.add(snapshotOf(portfolio, day, states, symbols, market, today));
        }

        snapshotRepository.saveAll(snapshots);
        log.debug("Historique portefeuille {} : {} jours recalculés depuis {}", portfolioId, snapshots.size(), start);
        return snapshots.size();
    }

    private PortfolioSnapshot snapshotOf(Portfolio portfolio, LocalDate day,
            Map<UUID, PositionState> states, Map<UUID, String> symbols,
            MarketData market, LocalDate today) {

        BigDecimal value = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;

        for (Map.Entry<UUID, PositionState> entry : states.entrySet()) {
            PositionState state = entry.getValue();
            invested = invested.add(state.getCostBasisEur());
            if (!state.isOpen()) {
                continue;
            }

            // Dernier cours connu à ce jour ; à défaut (actif pas encore dans
            // l'historique Yahoo), le prix de la dernière transaction : mieux
            // qu'un 0 qui créerait un faux décrochage sur la courbe.
            DailyPrice quote = floor(market.prices().get(symbols.get(entry.getKey())), day);
            BigDecimal price = quote != null ? quote.price() : state.getLastTradePrice();
            String currency = quote != null ? quote.currency() : state.getLastTradeCurrency();
            if (price == null) {
                continue;
            }

            BigDecimal priceEur = price.multiply(market.rateToEur(currency, day, today));
            value = value.add(state.getQuantity().multiply(priceEur)
                    .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING));
        }

        value = value.setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
        invested = invested.setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
        BigDecimal gain = value.subtract(invested);

        return PortfolioSnapshot.builder()
                .portfolio(portfolio)
                .snapshotDate(day)
                .totalValue(value)
                .totalInvested(invested)
                .gainLoss(gain)
                .gainLossPercentage(invested.signum() == 0
                        ? BigDecimal.ZERO
                        : gain.multiply(BigDecimal.valueOf(100))
                                .divide(invested, MoneyConstants.PERCENT_SCALE, MoneyConstants.ROUNDING))
                .baseCurrency(MoneyConstants.BASE_CURRENCY)
                .build();
    }

    // ============================================================ données marché

    private MarketData loadMarketData(List<Transaction> txs, LocalDate from, LocalDate to) {
        Set<String> assetSymbols = txs.stream()
                .map(t -> t.getAsset().getSymbol())
                .collect(Collectors.toSet());
        Map<String, NavigableMap<LocalDate, DailyPrice>> prices = priceHistoryService.loadSeries(assetSymbols, from, to);

        // Devises à convertir : celles des cours ET celles des transactions
        // (utilisées en repli quand aucun cours n'existe).
        Set<String> currencies = new java.util.HashSet<>();
        prices.values().forEach(series -> series.values().forEach(p -> currencies.add(p.currency())));
        txs.forEach(t -> currencies.add(t.getCurrency()));
        Map<String, String> pairByCurrency = currencies.stream()
                .filter(PortfolioHistoryService::isForeign)
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        c -> FxSymbols.pair(c, MoneyConstants.BASE_CURRENCY)));

        Map<String, NavigableMap<LocalDate, DailyPrice>> fxByPair = priceHistoryService
                .loadSeries(pairByCurrency.values(), from, to);
        Map<String, NavigableMap<LocalDate, DailyPrice>> fx = new HashMap<>();
        pairByCurrency.forEach((currency, pair) -> {
            if (fxByPair.containsKey(pair)) {
                fx.put(currency, fxByPair.get(pair));
            }
        });

        return new MarketData(prices, fx, currencyConverter.openSession());
    }

    /**
     * Séries chargées en mémoire pour une reconstruction.
     * Taux : historique de la paire au jour J ; aujourd'hui (ou historique
     * absent), taux courant — cohérent avec la valorisation live du dashboard.
     */
    private record MarketData(Map<String, NavigableMap<LocalDate, DailyPrice>> prices,
            Map<String, NavigableMap<LocalDate, DailyPrice>> fx,
            CurrencyConverter.Session liveFx) {

        BigDecimal rateToEur(String currency, LocalDate day, LocalDate today) {
            if (!isForeign(currency)) {
                return BigDecimal.ONE;
            }
            String iso = currency.toUpperCase();
            if (day.isBefore(today)) {
                DailyPrice historical = floor(fx.get(iso), day);
                if (historical != null) {
                    return historical.price();
                }
            }
            return liveFx.rate(iso, MoneyConstants.BASE_CURRENCY);
        }
    }

    // ==================================================================== utils

    private static DailyPrice floor(NavigableMap<LocalDate, DailyPrice> series, LocalDate day) {
        if (series == null) {
            return null;
        }
        Map.Entry<LocalDate, DailyPrice> entry = series.floorEntry(day);
        return entry != null ? entry.getValue() : null;
    }

    private static boolean isForeign(String currency) {
        return currency != null && !currency.isBlank()
                && !Objects.equals(currency.toUpperCase(), MoneyConstants.BASE_CURRENCY);
    }

    private static LocalDate day(Transaction tx) {
        return tx.getTransactionDate().toLocalDate();
    }
}

package com.portfolio.tracker.assetprice;

import com.portfolio.tracker.asset.ManualAssets;

import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketDataUnavailableException;
import com.portfolio.tracker.marketdata.MarketPricePoint;
import com.portfolio.tracker.marketdata.MarketQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Historique journalier des symboles (actifs ET paires de devises).
 *
 * Règles :
 * - une ligne par (symbole, jour) : on met à jour en place, on ne duplique jamais ;
 * - on ne redemande au provider que les jours jamais demandés, grâce à
 *   {@link PriceHistoryCoverage} (pas de trou possible au milieu de l'historique) ;
 * - les appels HTTP se font HORS transaction, les écritures dans des
 *   transactions courtes et indépendantes (REQUIRES_NEW), ce qui rend le service
 *   utilisable depuis un listener AFTER_COMMIT comme depuis un scheduler.
 */
@Service
@Slf4j
public class PriceHistoryService {

    static final String SOURCE_YAHOO = "YAHOO_FINANCE";

    private final AssetPriceRepository priceRepository;
    private final PriceHistoryCoverageRepository coverageRepository;
    private final MarketDataProvider marketDataProvider;
    private final TransactionTemplate txNew;

    public PriceHistoryService(AssetPriceRepository priceRepository,
            PriceHistoryCoverageRepository coverageRepository,
            MarketDataProvider marketDataProvider,
            PlatformTransactionManager transactionManager) {
        this.priceRepository = priceRepository;
        this.coverageRepository = coverageRepository;
        this.marketDataProvider = marketDataProvider;
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------ couverture

    /**
     * Garantit que l'historique de {@code symbol} couvre [from, aujourd'hui].
     * Ne télécharge que les plages jamais demandées. Idempotent, ne lève jamais.
     *
     * @return false si une plage n'a pas pu être récupérée (à retenter plus tard)
     */
    public boolean ensureCoverage(String symbol, LocalDate from) {
        if (symbol == null || symbol.isBlank() || from == null || ManualAssets.isManual(symbol)) {
            return true; // actif non coté : ses valeurs sont saisies, rien à télécharger
        }
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate start = from.isAfter(today) ? today : from;

        PriceHistoryCoverage coverage = txNew.execute(s -> coverageRepository.findById(symbol).orElse(null));

        boolean complete = true;
        for (DateRange range : missingRanges(coverage, start, today, yesterday)) {
            complete &= fetchRange(symbol, range, yesterday);
        }

        // Aucun point du tout (ex : achat saisi un samedi sur une action) :
        // on stocke au moins la cotation courante pour pouvoir valoriser.
        if (!Boolean.TRUE.equals(txNew.execute(s -> priceRepository.existsBySymbol(symbol)))) {
            marketDataProvider.getQuote(symbol).ifPresent(q -> saveQuote(symbol, q));
        }
        return complete;
    }

    private boolean fetchRange(String symbol, DateRange range, LocalDate yesterday) {
        try {
            List<MarketPricePoint> points = marketDataProvider.getDailyHistory(symbol, range.from(), range.to());
            int written = Optional.ofNullable(txNew.execute(s -> {
                int n = upsertPoints(symbol, range, points);
                // Seuls les jours clos comptent comme couverts : aujourd'hui sera
                // redemandé tant que la journée n'est pas terminée.
                extendCoverage(symbol, range.from(), range.to().isAfter(yesterday) ? yesterday : range.to());
                return n;
            })).orElse(0);
            log.info("Historique {} [{} → {}] : {} points reçus, {} écrits",
                    symbol, range.from(), range.to(), points.size(), written);
            return true;
        } catch (MarketDataUnavailableException e) {
            log.warn("Historique {} indisponible [{} → {}], nouvel essai au prochain rattrapage : {}",
                    symbol, range.from(), range.to(), e.getMessage());
            return false;
        } catch (DataIntegrityViolationException e) {
            log.warn("Écriture concurrente de l'historique {} ignorée", symbol);
            return false;
        }
    }

    /**
     * Plages à demander au provider. L'intervalle couvert reste contigu :
     * on ne demande jamais [from, x] en laissant un trou avant coveredFrom.
     */
    static List<DateRange> missingRanges(PriceHistoryCoverage coverage, LocalDate from,
            LocalDate today, LocalDate yesterday) {
        if (coverage == null) {
            return List.of(new DateRange(from, today));
        }
        List<DateRange> ranges = new ArrayList<>(2);
        if (from.isBefore(coverage.getCoveredFrom())) {
            ranges.add(new DateRange(from, coverage.getCoveredFrom().minusDays(1)));
        }
        if (coverage.getCoveredTo().isBefore(yesterday)) {
            LocalDate next = coverage.getCoveredTo().plusDays(1);
            // coveredTo peut précéder coveredFrom (couverture vide) : ne pas redemander avant from
            ranges.add(new DateRange(next.isBefore(coverage.getCoveredFrom()) ? coverage.getCoveredFrom() : next,
                    today));
        }
        return ranges;
    }

    private int upsertPoints(String symbol, DateRange range, List<MarketPricePoint> points) {
        if (points.isEmpty()) {
            return 0;
        }
        Map<LocalDate, AssetPrice> existing = priceRepository
                .findBySymbolAndPriceDateBetween(symbol, range.from(), range.to()).stream()
                .collect(Collectors.toMap(AssetPrice::getPriceDate, Function.identity(), (a, b) -> a));

        // Yahoo peut renvoyer deux barres pour un même jour : la dernière gagne
        Map<LocalDate, MarketPricePoint> byDay = new LinkedHashMap<>();
        points.forEach(p -> byDay.put(p.date(), p));

        List<AssetPrice> toSave = new ArrayList<>(byDay.size());
        byDay.forEach((day, p) -> {
            AssetPrice row = existing.getOrDefault(day,
                    AssetPrice.builder().symbol(symbol).priceDate(day).build());
            row.setPrice(p.price());
            row.setCurrency(p.currency());
            row.setLastUpdated(p.asOf());
            row.setSource(SOURCE_YAHOO);
            toSave.add(row);
        });
        priceRepository.saveAll(toSave);
        return toSave.size();
    }

    private void extendCoverage(String symbol, LocalDate from, LocalDate to) {
        PriceHistoryCoverage coverage = coverageRepository.findById(symbol)
                .orElseGet(() -> PriceHistoryCoverage.builder()
                        .symbol(symbol).coveredFrom(from).coveredTo(to).build());
        if (from.isBefore(coverage.getCoveredFrom())) {
            coverage.setCoveredFrom(from);
        }
        if (to.isAfter(coverage.getCoveredTo())) {
            coverage.setCoveredTo(to);
        }
        coverageRepository.save(coverage);
    }

    // --------------------------------------------------------------- cotation

    /** Enregistre (ou met à jour) le prix du jour de bourse de la cotation. */
    public void saveQuote(String symbol, MarketQuote quote) {
        LocalDate day = quote.marketDate() != null ? quote.marketDate() : LocalDate.now();
        try {
            txNew.executeWithoutResult(s -> {
                AssetPrice row = priceRepository.findBySymbolAndPriceDate(symbol, day)
                        .orElseGet(() -> AssetPrice.builder().symbol(symbol).priceDate(day).build());
                row.setPrice(quote.price());
                row.setCurrency(quote.currency());
                row.setLastUpdated(quote.asOf() != null ? quote.asOf() : LocalDateTime.now());
                row.setSource(SOURCE_YAHOO);
                priceRepository.save(row);
            });
        } catch (DataIntegrityViolationException e) {
            log.warn("Écriture concurrente du prix {} au {} ignorée", symbol, day);
        }
    }

    // ------------------------------------------------------------- lecture

    /**
     * Charge en mémoire les séries de plusieurs symboles sur [from, to], plus le
     * dernier point antérieur à {@code from} (amorce). Deux requêtes au total,
     * quel que soit le nombre de symboles ou de jours.
     */
    public Map<String, NavigableMap<LocalDate, DailyPrice>> loadSeries(Collection<String> symbols,
            LocalDate from, LocalDate to) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, NavigableMap<LocalDate, DailyPrice>> series = new HashMap<>();
        priceRepository.findLastBeforeForSymbols(symbols, from).forEach(ap -> put(series, ap));
        priceRepository.findBySymbolInAndPriceDateBetweenOrderByPriceDateAsc(symbols, from, to)
                .forEach(ap -> put(series, ap));
        return series;
    }

    private static void put(Map<String, NavigableMap<LocalDate, DailyPrice>> series, AssetPrice ap) {
        series.computeIfAbsent(ap.getSymbol(), k -> new TreeMap<>())
                .put(ap.getPriceDate(), new DailyPrice(ap.getPriceDate(), ap.getPrice(), ap.getCurrency()));
    }

    /** Dernier prix connu au jour {@code date} inclus. */
    public Optional<DailyPrice> findOnOrBefore(String symbol, LocalDate date) {
        return txNew.execute(s -> priceRepository
                .findTopBySymbolAndPriceDateLessThanEqualOrderByPriceDateDesc(symbol, date)
                .map(ap -> new DailyPrice(ap.getPriceDate(), ap.getPrice(), ap.getCurrency())));
    }

    record DateRange(LocalDate from, LocalDate to) {
    }
}

package com.portfolio.tracker.performance;

import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.exchangerate.FxSymbols;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.performance.PerformanceCalculator.Day;
import com.portfolio.tracker.performance.PerformanceCalculator.Result;
import com.portfolio.tracker.performance.dto.PerformanceResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Performance réelle d'un portefeuille ou du patrimoine sur une période, à
 * partir des snapshots journaliers (valeur pour la performance + flux
 * externes, voir {@code PerformanceFlows}), et comparaison à un indice.
 *
 * Aucune transaction longue : lecture des snapshots, puis (hors transaction)
 * téléchargement éventuel de l'historique de l'indice.
 */
@Service
@Slf4j
public class PerformanceService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioRepository portfolioRepository;
    private final PriceHistoryService priceHistoryService;
    private final MarketDataProvider marketDataProvider;
    private final CurrencyConverter currencyConverter;
    private final TransactionTemplate tx;

    public PerformanceService(PortfolioSnapshotRepository snapshotRepository, PortfolioRepository portfolioRepository,
            PriceHistoryService priceHistoryService, MarketDataProvider marketDataProvider,
            CurrencyConverter currencyConverter, PlatformTransactionManager transactionManager) {
        this.snapshotRepository = snapshotRepository;
        this.portfolioRepository = portfolioRepository;
        this.priceHistoryService = priceHistoryService;
        this.marketDataProvider = marketDataProvider;
        this.currencyConverter = currencyConverter;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setReadOnly(true);
    }

    /** Ligne de snapshot détachée de JPA (lue une fois, dans la transaction). */
    private record Row(UUID portfolioId, LocalDate date, double value, double flow) {
    }

    private record Loaded(List<Row> rows, Map<UUID, Portfolio> portfolios) {
    }

    /**
     * @param portfolioId null = tout le patrimoine
     * @param benchmark   symbole Yahoo de l'indice (null = aucun)
     */
    public PerformanceResponse performance(UUID userId, UUID portfolioId, String periodCode, String benchmark) {
        PerformancePeriod period = PerformancePeriod.fromCode(periodCode);
        LocalDate today = LocalDate.now();
        LocalDate requestedStart = period.start(today);

        Loaded loaded = Objects.requireNonNull(tx.execute(s -> load(userId, portfolioId, requestedStart)));
        List<Row> rows = loaded.rows();
        LocalDate firstDay = rows.stream().map(Row::date).min(Comparator.naturalOrder()).orElse(today);
        // La période ne commence pas avant le premier jour d'historique
        LocalDate start = requestedStart == null || requestedStart.isBefore(firstDay) ? firstDay : requestedStart;
        LocalDate base = start.minusDays(1);

        Result total = compute(rows, base, today);
        List<PerformanceResponse.PortfolioPerformance> breakdown = portfolioId != null ? List.of()
                : breakdown(rows, loaded.portfolios(), base, today);

        List<LocalDate> dates = datesBetween(start, today);
        Map<LocalDate, Double> bench = benchmark == null || benchmark.isBlank() ? Map.of()
                : benchmarkSeries(benchmark.trim(), base, today);
        List<PerformanceResponse.Point> series = new ArrayList<>(dates.size());
        Map<LocalDate, Double> valueByDate = sumByDate(rows, Row::value);
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            Double b = bench.get(d);
            series.add(new PerformanceResponse.Point(d, money(valueByDate.getOrDefault(d, 0.0)),
                    pct(total.twrCumulative().get(i)), b != null ? pct(b) : null));
        }

        PerformanceResponse.Benchmark benchmarkInfo = null;
        if (!bench.isEmpty()) {
            Double last = bench.get(today);
            benchmarkInfo = new PerformanceResponse.Benchmark(benchmark.trim(), benchmarkName(benchmark.trim()),
                    last != null ? pct(last) : null);
        }

        long days = start.until(today, java.time.temporal.ChronoUnit.DAYS) + 1;
        return new PerformanceResponse(period.code(), start, today,
                money(total.startValue()), money(total.endValue()), money(total.netFlows()), money(total.gain()),
                pct(total.twr()), days >= 365 ? pct(annualize(total.twr(), days)) : null,
                pct(total.mwr()), days >= 365 && total.xirr() != null ? pct(total.xirr()) : null,
                benchmarkInfo, series, breakdown, RiskCalculator.compute(dates, total.twrCumulative()));
    }

    // ----------------------------------------------------------------- lecture

    private Loaded load(UUID userId, UUID portfolioId, LocalDate requestedStart) {
        Map<UUID, Portfolio> portfolios = new LinkedHashMap<>();
        List<PortfolioSnapshot> snapshots;
        if (portfolioId != null) {
            Portfolio p = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
            portfolios.put(p.getId(), p);
            snapshots = requestedStart == null
                    ? snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId)
                    : snapshotRepository.findByPortfolioIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                            portfolioId, requestedStart.minusDays(1));
        } else {
            portfolioRepository.findByUserId(userId).forEach(p -> portfolios.put(p.getId(), p));
            snapshots = requestedStart == null
                    ? snapshotRepository.findAllByUserIdOrderBySnapshotDateAsc(userId)
                    : snapshotRepository.findByUserIdAndSnapshotDateGreaterThanEqual(userId, requestedStart.minusDays(1));
        }
        List<Row> rows = snapshots.stream()
                .map(s -> new Row(s.getPortfolio().getId(), s.getSnapshotDate(),
                        num(s.getPerformanceValue() != null ? s.getPerformanceValue() : s.getTotalValue()),
                        num(s.getNetFlow())))
                .toList();
        return new Loaded(rows, portfolios);
    }

    // ----------------------------------------------------------------- calcul

    /** Agrège les lignes par jour puis calcule ; la veille du début sert de base. */
    private static Result compute(List<Row> rows, LocalDate base, LocalDate today) {
        Map<LocalDate, Double> values = sumByDate(rows, Row::value);
        Map<LocalDate, Double> flows = sumByDate(rows, Row::flow);
        List<Day> days = new ArrayList<>();
        for (LocalDate d : datesBetween(base.plusDays(1), today)) {
            days.add(new Day(d, values.getOrDefault(d, 0.0), flows.getOrDefault(d, 0.0)));
        }
        return PerformanceCalculator.compute(base, values.getOrDefault(base, 0.0), days);
    }

    private static List<PerformanceResponse.PortfolioPerformance> breakdown(List<Row> rows,
            Map<UUID, Portfolio> portfolios, LocalDate base, LocalDate today) {
        Map<UUID, List<Row>> byPortfolio = rows.stream().collect(Collectors.groupingBy(Row::portfolioId));
        long days = base.until(today, java.time.temporal.ChronoUnit.DAYS);
        List<PerformanceResponse.PortfolioPerformance> out = new ArrayList<>();
        for (Portfolio p : portfolios.values()) {
            List<Row> own = byPortfolio.get(p.getId());
            if (own == null || own.isEmpty()) {
                continue;
            }
            // Portefeuille ouvert en cours de période : sa propre période commence à son premier jour
            LocalDate first = own.stream().map(Row::date).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate ownBase = first.isAfter(base) ? first.minusDays(1) : base;
            Result r = compute(own, ownBase, today);
            long ownDays = ownBase.until(today, java.time.temporal.ChronoUnit.DAYS);
            out.add(new PerformanceResponse.PortfolioPerformance(p.getId(), p.getName(), p.getType(),
                    money(r.endValue()), money(r.gain()), pct(r.twr()), pct(r.mwr()),
                    ownDays >= 365 && days >= 365 && r.xirr() != null ? pct(r.xirr()) : null));
        }
        out.sort(Comparator.comparing(PerformanceResponse.PortfolioPerformance::valueEur).reversed());
        return out;
    }

    // ------------------------------------------------------------------ indice

    /**
     * Variation de l'indice en EUR depuis {@code base}, jour par jour (dernier
     * cours connu les jours fériés). Vide si l'indice est introuvable.
     */
    private Map<LocalDate, Double> benchmarkSeries(String symbol, LocalDate base, LocalDate today) {
        try {
            // Quelques jours de marge : la veille du début peut être un week-end
            LocalDate from = base.minusDays(7);
            priceHistoryService.ensureCoverage(symbol, from);
            NavigableMap<LocalDate, DailyPrice> prices = priceHistoryService
                    .loadSeries(List.of(symbol), from, today).get(symbol);
            if (prices == null || prices.isEmpty()) {
                return Map.of();
            }
            String currency = prices.lastEntry().getValue().currency();
            NavigableMap<LocalDate, DailyPrice> fx = null;
            CurrencyConverter.Session live = currencyConverter.openSession();
            if (currency != null && !currency.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY)) {
                String pair = FxSymbols.pair(currency, MoneyConstants.BASE_CURRENCY);
                priceHistoryService.ensureCoverage(pair, from);
                fx = priceHistoryService.loadSeries(List.of(pair), from, today).get(pair);
            }
            Double baseEur = priceEur(prices, fx, live, currency, base, today);
            if (baseEur == null || baseEur <= 0) {
                return Map.of();
            }
            Map<LocalDate, Double> out = new HashMap<>();
            for (LocalDate d : datesBetween(base.plusDays(1), today)) {
                Double eur = priceEur(prices, fx, live, currency, d, today);
                if (eur != null) {
                    out.put(d, eur / baseEur - 1);
                }
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("Indice {} indisponible : {}", symbol, e.getMessage());
            return Map.of();
        }
    }

    private static Double priceEur(NavigableMap<LocalDate, DailyPrice> prices, NavigableMap<LocalDate, DailyPrice> fx,
            CurrencyConverter.Session live, String currency, LocalDate day, LocalDate today) {
        Map.Entry<LocalDate, DailyPrice> p = prices.floorEntry(day);
        if (p == null) {
            return null;
        }
        double rate = 1;
        if (currency != null && !currency.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY)) {
            Map.Entry<LocalDate, DailyPrice> r = fx != null && day.isBefore(today) ? fx.floorEntry(day) : null;
            rate = r != null ? r.getValue().price().doubleValue()
                    : live.rate(currency, MoneyConstants.BASE_CURRENCY).doubleValue();
        }
        return p.getValue().price().doubleValue() * rate;
    }

    private String benchmarkName(String symbol) {
        try {
            return marketDataProvider.getQuote(symbol).map(q -> q.longName() != null ? q.longName() : symbol)
                    .orElse(symbol);
        } catch (RuntimeException e) {
            return symbol;
        }
    }

    // ------------------------------------------------------------------- utils

    private static Map<LocalDate, Double> sumByDate(List<Row> rows, java.util.function.ToDoubleFunction<Row> f) {
        return rows.stream().collect(Collectors.groupingBy(Row::date, Collectors.summingDouble(f)));
    }

    private static List<LocalDate> datesBetween(LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(d);
        }
        return out;
    }

    private static double annualize(double cumulative, long days) {
        return Math.pow(1 + cumulative, 365.0 / days) - 1;
    }

    private static double num(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(MoneyConstants.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 0.1234 → 12.34 */
    private static BigDecimal pct(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(MoneyConstants.PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}

package com.portfolio.tracker.income;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.income.dto.IncomeResponse;
import com.portfolio.tracker.income.dto.IncomeResponse.MonthIncome;
import com.portfolio.tracker.income.dto.IncomeResponse.PositionIncome;
import com.portfolio.tracker.income.dto.IncomeResponse.UpcomingPayment;
import com.portfolio.tracker.marketdata.DividendEvent;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revenus passifs : dividendes et intérêts reçus (saisies de l'utilisateur),
 * revenus attendus sur 12 mois (dividendes des 12 derniers mois de chaque
 * ligne détenue, intérêts des livrets au taux affiché), calendrier estimé.
 *
 * Les dividendes par action viennent du fournisseur de marché, mis en cache
 * 12 h par symbole (ils ne changent qu'au détachement).
 */
@Service
@Slf4j
public class IncomeService {

    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final int RECEIVED_MONTHS = 24;

    private final PortfolioValuationService valuationService;
    private final TransactionRepository transactionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final MarketDataProvider marketDataProvider;
    private final CurrencyConverter currencyConverter;
    private final TransactionTemplate tx;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<DividendEvent> events, Instant at) {
    }

    public IncomeService(PortfolioValuationService valuationService, TransactionRepository transactionRepository,
            CashMovementRepository cashMovementRepository, MarketDataProvider marketDataProvider,
            CurrencyConverter currencyConverter, PlatformTransactionManager transactionManager) {
        this.valuationService = valuationService;
        this.transactionRepository = transactionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.marketDataProvider = marketDataProvider;
        this.currencyConverter = currencyConverter;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setReadOnly(true);
    }

    public IncomeResponse income(UUID userId) {
        LocalDate today = LocalDate.now();
        ValuationResult valuation = Objects.requireNonNull(tx.execute(s -> valuationService.valuate(userId, null, null)));
        CurrencyConverter.Session fx = currencyConverter.openSession();

        List<PositionIncome> positions = new ArrayList<>();
        List<UpcomingPayment> upcoming = new ArrayList<>();
        BigDecimal payingCost = BigDecimal.ZERO;

        for (PortfolioValuation p : valuation.getPortfolios()) {
            // Livret : intérêts au taux affiché, versés au 31 décembre
            if (p.getType() == PortfolioType.LIVRET) {
                if (p.getAnnualInterestRate() != null && p.getCashEur() != null && p.getCashEur().signum() > 0) {
                    BigDecimal annual = p.getCashEur().multiply(p.getAnnualInterestRate()).movePointLeft(2);
                    positions.add(new PositionIncome(p.getPortfolioId(), p.getName(), null, p.getName(), null,
                            null, "EUR", money(annual), p.getAnnualInterestRate(), p.getAnnualInterestRate(), 1,
                            null, "INTEREST"));
                    upcoming.add(new UpcomingPayment(LocalDate.of(today.getYear(), 12, 31), null, p.getName(),
                            money(annual), "INTEREST", true));
                }
                continue;
            }
            for (PositionValuation pos : p.getPositions()) {
                if (pos.getQuantity() == null || pos.getQuantity().signum() <= 0
                        || pos.getAssetType() == AssetType.CRYPTO) {
                    continue;
                }
                List<DividendEvent> lastYear = DividendProjection.lastYear(dividends(pos.getSymbol(), today), today);
                if (lastYear.isEmpty()) {
                    continue; // ne verse pas (ou capitalisant)
                }
                String currency = lastYear.get(lastYear.size() - 1).currency();
                BigDecimal rate = fx.rate(currency != null ? currency : "EUR", MoneyConstants.BASE_CURRENCY);
                BigDecimal perShare = DividendProjection.perShare(lastYear);
                BigDecimal annual = perShare.multiply(pos.getQuantity()).multiply(rate);
                payingCost = payingCost.add(nz(pos.getInvestedEur()));
                positions.add(new PositionIncome(p.getPortfolioId(), p.getName(), pos.getSymbol(), pos.getName(),
                        pos.getQuantity(), perShare, currency, money(annual), pct(annual, pos.getInvestedEur()),
                        pct(annual, pos.getCurrentValueEur()), lastYear.size(),
                        lastYear.get(lastYear.size() - 1).exDate(), "DIVIDEND"));
                for (DividendEvent next : DividendProjection.nextYear(lastYear, today)) {
                    upcoming.add(new UpcomingPayment(next.exDate(), pos.getSymbol(), pos.getName(),
                            money(next.amount().multiply(pos.getQuantity()).multiply(rate)), "DIVIDEND", true));
                }
            }
        }

        BigDecimal annual = positions.stream().map(PositionIncome::annualEur).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dividendsAnnual = positions.stream().filter(x -> "DIVIDEND".equals(x.kind()))
                .map(PositionIncome::annualEur).reduce(BigDecimal.ZERO, BigDecimal::add);
        positions.sort(Comparator.comparing(PositionIncome::annualEur).reversed());
        upcoming.sort(Comparator.comparing(UpcomingPayment::date));

        List<MonthIncome> received = received(userId, today);
        YearMonth thisMonth = YearMonth.from(today);
        BigDecimal last12 = received.stream()
                .filter(m -> !YearMonth.parse(m.month()).isBefore(thisMonth.minusMonths(11)))
                .map(m -> m.dividendsEur().add(m.interestEur())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal thisYear = received.stream()
                .filter(m -> YearMonth.parse(m.month()).getYear() == today.getYear())
                .map(m -> m.dividendsEur().add(m.interestEur())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new IncomeResponse(money(annual), money(annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)),
                money(last12), money(thisYear), pct(dividendsAnnual, payingCost), positions, received, upcoming);
    }

    /** Reçu par mois : dividendes saisis (nets de frais) et intérêts (mouvements d'argent). */
    private List<MonthIncome> received(UUID userId, LocalDate today) {
        YearMonth first = YearMonth.from(today).minusMonths(RECEIVED_MONTHS - 1L);
        Map<YearMonth, BigDecimal[]> byMonth = new TreeMap<>();
        for (YearMonth m = first; !m.isAfter(YearMonth.from(today)); m = m.plusMonths(1)) {
            byMonth.put(m, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        }
        tx.executeWithoutResult(s -> {
            for (Transaction t : transactionRepository.findAllForValuation(userId, null, null)) {
                if (t.getType() != TransactionType.DIVIDEND) {
                    continue;
                }
                BigDecimal[] slot = byMonth.get(YearMonth.from(t.getTransactionDate()));
                if (slot != null) {
                    slot[0] = slot[0].add(nz(t.getTotalAmountEur()).subtract(nz(t.getFeesEur())));
                }
            }
            for (CashMovement m : cashMovementRepository.findAllForValuation(userId, null)) {
                if (m.getType() != CashMovementType.INTEREST) {
                    continue;
                }
                BigDecimal[] slot = byMonth.get(YearMonth.from(m.getMovementDate()));
                if (slot != null) {
                    slot[1] = slot[1].add(m.getAmount());
                }
            }
        });
        return byMonth.entrySet().stream()
                .map(e -> new MonthIncome(e.getKey().toString(), money(e.getValue()[0]), money(e.getValue()[1])))
                .toList();
    }

    /** Dividendes des 13 derniers mois d'un symbole (cache 12 h ; vide si le fournisseur échoue). */
    private List<DividendEvent> dividends(String symbol, LocalDate today) {
        Cached c = cache.get(symbol);
        if (c != null && c.at().plus(CACHE_TTL).isAfter(Instant.now())) {
            return c.events();
        }
        try {
            List<DividendEvent> events = marketDataProvider.getDividends(symbol, today.minusDays(400), today);
            cache.put(symbol, new Cached(events, Instant.now()));
            return events;
        } catch (RuntimeException e) {
            log.warn("Dividendes de {} indisponibles : {}", symbol, e.getMessage());
            return c != null ? c.events() : List.of();
        }
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, MoneyConstants.PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MoneyConstants.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

package com.portfolio.tracker.analysis;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.PositionState;
import com.portfolio.tracker.dashboard.dto.PositionValuation;
import com.portfolio.tracker.exchangerate.FxSymbols;
import com.portfolio.tracker.performance.PerformancePeriod;
import com.portfolio.tracker.shared.CurrencyConverter;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * « Qui a fait ta performance » : ce que chaque ligne a rapporté sur la
 * période (plus-value, réalisée ou non, et dividendes, frais déduits).
 *
 * gain = valeur finale − valeur au début − (achats − ventes − dividendes nets
 * de la période). Valeur au début : quantité détenue la veille × dernier cours
 * connu (à défaut, prix de la dernière opération), au taux de change du jour.
 * Tout est lu en base (cours déjà téléchargés), aucun appel réseau.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContributionService {

    private final TransactionRepository transactionRepository;
    private final PortfolioValuationService valuationService;
    private final PriceHistoryService priceHistoryService;
    private final CurrencyConverter currencyConverter;

    public record Line(UUID assetId, UUID portfolioId, String portfolioName, String symbol, String name,
                       AssetType assetType, double startValueEur, double endValueEur, double flowsEur, double gainEur,
                       Double returnPct, double weightPct) {
    }

    public record Report(String period, LocalDate from, LocalDate to, double gainEur, double endValueEur,
                         List<Line> lines) {
    }

    public Report contributions(UUID userId, UUID portfolioId, String periodCode) {
        PerformancePeriod period = PerformancePeriod.fromCode(periodCode);
        LocalDate today = LocalDate.now();
        List<Transaction> txs = transactionRepository.findAllForValuation(userId, portfolioId, null);
        LocalDate first = txs.stream().map(t -> t.getTransactionDate().toLocalDate()).min(Comparator.naturalOrder())
                .orElse(today);
        LocalDate requested = period.start(today);
        LocalDate start = requested == null || requested.isBefore(first) ? first : requested;
        LocalDate eve = start.minusDays(1);

        Map<UUID, PositionValuation> current = new HashMap<>();
        Map<UUID, String> portfolioNames = new HashMap<>();
        valuationService.valuate(userId, portfolioId, null).getPortfolios().forEach(p -> {
            portfolioNames.put(p.getPortfolioId(), p.getName());
            p.getPositions().forEach(pos -> current.put(pos.getAssetId(), pos));
        });

        Map<UUID, List<Transaction>> byAsset = txs.stream().collect(Collectors.groupingBy(t -> t.getAsset().getId()));
        Set<String> symbols = txs.stream().map(t -> t.getAsset().getSymbol()).collect(Collectors.toSet());
        Map<String, NavigableMap<LocalDate, DailyPrice>> prices = priceHistoryService.loadSeries(symbols, eve, eve);
        FxAtDate fx = new FxAtDate(prices, eve);

        List<Line> lines = new ArrayList<>();
        double totalEnd = current.values().stream().mapToDouble(p -> p.getCurrentValueEur().doubleValue()).sum();
        double totalGain = 0;
        for (List<Transaction> assetTxs : byAsset.values()) {
            assetTxs.sort(Transaction.CHRONOLOGICAL);
            Asset asset = assetTxs.get(0).getAsset();
            PositionState before = new PositionState();
            double flows = 0, bought = 0;
            for (Transaction t : assetTxs) {
                if (t.getTransactionDate().toLocalDate().isBefore(start)) {
                    before.apply(t);
                    continue;
                }
                double amount = nz(t.getTotalAmountEur()), fees = nz(t.getFeesEur());
                switch (t.getType()) {
                    case BUY -> {
                        flows += amount + fees;
                        bought += amount + fees;
                    }
                    case SELL, DIVIDEND -> flows -= amount - fees;
                }
            }
            double startValue = 0;
            if (before.isOpen()) {
                DailyPrice p = floor(prices.get(asset.getSymbol()), eve);
                BigDecimal price = p != null ? p.price() : before.getLastTradePrice();
                String currency = p != null ? p.currency() : before.getLastTradeCurrency();
                if (price != null) {
                    startValue = before.getQuantity().doubleValue() * price.doubleValue() * fx.rate(currency);
                }
            }
            PositionValuation now = current.get(asset.getId());
            double endValue = now != null ? now.getCurrentValueEur().doubleValue() : 0;
            if (startValue == 0 && flows == 0 && endValue == 0) {
                continue; // rien sur la période
            }
            double gain = endValue - startValue - flows;
            double invested = startValue + bought;
            totalGain += gain;
            lines.add(new Line(asset.getId(), asset.getPortfolio().getId(), portfolioNames.get(asset.getPortfolio().getId()),
                    asset.getSymbol(), asset.getName(), asset.getAssetType(), round(startValue), round(endValue),
                    round(flows), round(gain), invested > 0 ? round(gain / invested * 100) : null,
                    totalEnd > 0 ? round(endValue / totalEnd * 100) : 0));
        }
        lines.sort(Comparator.comparingDouble(Line::gainEur).reversed());
        return new Report(period.code(), start, today, round(totalGain), round(totalEnd), lines);
    }

    /** Taux vers l'euro la veille du début : historique si connu, sinon taux du jour. */
    private final class FxAtDate {
        private final Map<String, NavigableMap<LocalDate, DailyPrice>> series;
        private final LocalDate day;
        private final CurrencyConverter.Session live = currencyConverter.openSession();

        FxAtDate(Map<String, NavigableMap<LocalDate, DailyPrice>> prices, LocalDate day) {
            this.day = day;
            Set<String> pairs = prices.values().stream().flatMap(s -> s.values().stream()).map(DailyPrice::currency)
                    .filter(c -> c != null && !c.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY))
                    .map(c -> FxSymbols.pair(c, MoneyConstants.BASE_CURRENCY)).collect(Collectors.toSet());
            this.series = pairs.isEmpty() ? Map.of() : priceHistoryService.loadSeries(pairs, day, day);
        }

        double rate(String currency) {
            if (currency == null || currency.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY)) {
                return 1;
            }
            DailyPrice p = floor(series.get(FxSymbols.pair(currency, MoneyConstants.BASE_CURRENCY)), day);
            return p != null ? p.price().doubleValue() : live.rate(currency, MoneyConstants.BASE_CURRENCY).doubleValue();
        }
    }

    private static DailyPrice floor(NavigableMap<LocalDate, DailyPrice> series, LocalDate day) {
        if (series == null) {
            return null;
        }
        Map.Entry<LocalDate, DailyPrice> e = series.floorEntry(day);
        return e != null ? e.getValue() : null;
    }

    private static double nz(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

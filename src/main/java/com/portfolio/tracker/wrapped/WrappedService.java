package com.portfolio.tracker.wrapped;

import com.portfolio.tracker.analysis.ContributionService;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.performance.PerformanceService;
import com.portfolio.tracker.performance.dto.PerformanceResponse;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bilan d'une année (« Wrapped ») : performance comparée au MSCI World, mois
 * par mois, lignes stars et boulets, revenus, régularité, et un profil
 * d'investisseur ludique. Réutilise la performance et les contributions.
 */
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class WrappedService {

    static final String BENCHMARK = "CW8.PA";

    private final PerformanceService performanceService;
    private final ContributionService contributionService;
    private final TransactionRepository transactionRepository;
    private final CashMovementRepository cashMovementRepository;

    public record Line(String name, String symbol, UUID portfolioId, double gainEur, Double returnPct) {
    }

    public record Personality(String key, String title, String text) {
    }

    /**
     * @param complete  l'année est terminée
     * @param months    rendement de chaque mois en % (null : mois non couvert)
     * @param bestMonth 1 à 12
     */
    public record Wrapped(int year, List<Integer> years, LocalDate from, LocalDate to, boolean complete,
                          double startValueEur, double endValueEur, double gainEur, double netDepositsEur,
                          double twrPct, String benchmarkName, Double benchmarkPct, List<Double> months,
                          Integer bestMonth, Integer worstMonth, Line bestLine, Line worstLine,
                          double dividendsEur, double interestEur, int operations, int buys, int sells,
                          int activeMonths, double investedEur, double feesEur, int newAssets,
                          Personality personality) {
    }

    public Wrapped wrapped(UUID userId, Integer requestedYear) {
        List<Transaction> all = transactionRepository.findAllForValuation(userId, null, null);
        LocalDate today = LocalDate.now();
        TreeSet<Integer> years = all.stream().map(t -> t.getTransactionDate().getYear())
                .collect(Collectors.toCollection(TreeSet::new));
        if (years.isEmpty()) {
            throw new ResourceNotFoundException("Pas encore d'opération : ton premier bilan arrivera avec elles");
        }
        // Toutes les années depuis la première opération (une année sans opération a quand même une performance)
        for (int y = years.first(); y <= today.getYear(); y++) {
            years.add(y);
        }
        int year = requestedYear != null && years.contains(requestedYear) ? requestedYear
                : today.getMonthValue() == 12 || !years.contains(today.getYear() - 1) ? today.getYear() : today.getYear() - 1;
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = year == today.getYear() ? today : LocalDate.of(year, 12, 31);

        PerformanceResponse perf = performanceService.between(userId, null, String.valueOf(year), from, to, BENCHMARK);
        ContributionService.Report contributions = contributionService.between(userId, null, String.valueOf(year), from, to);

        Double[] months = monthly(perf.series());
        Integer best = null, worst = null;
        for (int m = 0; m < 12; m++) {
            if (months[m] == null) continue;
            if (best == null || months[m] > months[best - 1]) best = m + 1;
            if (worst == null || months[m] < months[worst - 1]) worst = m + 1;
        }
        List<ContributionService.Line> lines = contributions.lines();
        Line bestLine = lines.stream().filter(l -> !l.dataSuspect() && l.gainEur() > 0).max(Comparator.comparingDouble(ContributionService.Line::gainEur))
                .map(WrappedService::line).orElse(null);
        Line worstLine = lines.stream().filter(l -> !l.dataSuspect() && l.gainEur() < 0).min(Comparator.comparingDouble(ContributionService.Line::gainEur))
                .map(WrappedService::line).orElse(null);

        List<Transaction> yearTxs = all.stream().filter(t -> t.getTransactionDate().getYear() == year).toList();
        int buys = (int) yearTxs.stream().filter(t -> t.getType() == TransactionType.BUY).count();
        int sells = (int) yearTxs.stream().filter(t -> t.getType() == TransactionType.SELL).count();
        double dividends = yearTxs.stream().filter(t -> t.getType() == TransactionType.DIVIDEND)
                .mapToDouble(t -> nz(t.getTotalAmountEur()) - nz(t.getFeesEur())).sum();
        double invested = yearTxs.stream().filter(t -> t.getType() == TransactionType.BUY)
                .mapToDouble(t -> nz(t.getTotalAmountEur()) + nz(t.getFeesEur())).sum();
        double fees = yearTxs.stream().mapToDouble(t -> nz(t.getFeesEur())).sum();
        int activeMonths = (int) yearTxs.stream().filter(t -> t.getType() == TransactionType.BUY)
                .map(t -> t.getTransactionDate().getMonthValue()).distinct().count();
        Set<String> heldBefore = new HashSet<>();
        all.stream().filter(t -> t.getTransactionDate().getYear() < year).forEach(t -> heldBefore.add(t.getAsset().getSymbol()));
        int newAssets = (int) yearTxs.stream().filter(t -> t.getType() == TransactionType.BUY)
                .map(t -> t.getAsset().getSymbol()).filter(s -> !heldBefore.contains(s)).distinct().count();

        List<CashMovement> movements = cashMovementRepository.findAllForValuation(userId, null).stream()
                .filter(m -> m.getMovementDate().getYear() == year).toList();
        double interest = movements.stream().filter(m -> m.getType() == CashMovementType.INTEREST)
                .mapToDouble(m -> m.amountEur().doubleValue()).sum();
        fees += movements.stream().filter(m -> m.getType() == CashMovementType.FEE)
                .mapToDouble(m -> m.amountEur().doubleValue()).sum();

        double cryptoWeight = lines.stream().filter(l -> l.assetType() == AssetType.CRYPTO)
                .mapToDouble(ContributionService.Line::weightPct).sum();
        double twr = num(perf.twrPct());
        Double bench = perf.benchmark() != null && perf.benchmark().returnPct() != null
                ? num(perf.benchmark().returnPct()) : null;
        double endValue = num(perf.endValueEur());
        Personality personality = personality(cryptoWeight, activeMonths, sells, dividends + interest, endValue, twr, bench);

        return new Wrapped(year, new ArrayList<>(years.descendingSet()), perf.from(), to, year < today.getYear(),
                num(perf.startValueEur()), endValue, num(perf.gainEur()), num(perf.netFlowsEur()), twr,
                "MSCI World", bench, Arrays.asList(months), best, worst, bestLine, worstLine,
                round(dividends), round(interest), yearTxs.size(), buys, sells, activeMonths, round(invested),
                round(fees), newAssets, personality);
    }

    /** Profil ludique d'après l'année : le premier trait marquant l'emporte. */
    static Personality personality(double cryptoWeightPct, int activeMonths, int sells, double income, double endValue,
            double twr, Double benchmark) {
        if (cryptoWeightPct >= 25) {
            return new Personality("ADVENTURER", "L'aventurier",
                    "Plus d'un quart de ton patrimoine en crypto : tu aimes les montagnes russes, et elles te le rendent (parfois).");
        }
        if (activeMonths >= 10) {
            return new Personality("METRONOME", "Le métronome",
                    "Tu as investi " + activeMonths + " mois sur 12. La régularité bat presque toujours le bon moment.");
        }
        if (sells >= 5) {
            return new Personality("STRATEGIST", "Le stratège actif",
                    sells + " ventes cette année : tu fais tourner ton portefeuille. Surveille les frais et l'impôt.");
        }
        if (endValue > 0 && income / endValue >= 0.02) {
            return new Personality("RENTIER", "Le rentier en herbe",
                    "Tes placements te versent déjà plus de 2 % par an : l'argent travaille pendant que tu dors.");
        }
        if (benchmark != null && twr > benchmark) {
            return new Personality("HUNTER", "Le chasseur d'indice",
                    "Tu as fait mieux que le MSCI World. Peu d'investisseurs peuvent en dire autant.");
        }
        return new Personality("BUILDER", "Le bâtisseur patient",
                "Brique après brique, ton patrimoine se construit. Le temps est ton meilleur allié.");
    }

    /** Rendement de chaque mois (%) depuis la performance cumulée jour par jour. */
    static Double[] monthly(List<PerformanceResponse.Point> series) {
        Double[] months = new Double[12];
        double previous = 1;
        int currentMonth = -1;
        double lastIndex = 1;
        for (PerformanceResponse.Point p : series) {
            int m = p.date().getMonthValue() - 1;
            double index = 1 + num(p.twrPct()) / 100;
            if (currentMonth != -1 && m != currentMonth) {
                months[currentMonth] = round((lastIndex / previous - 1) * 100);
                previous = lastIndex;
            }
            currentMonth = m;
            lastIndex = index;
        }
        if (currentMonth != -1) {
            months[currentMonth] = round((lastIndex / previous - 1) * 100);
        }
        return months;
    }

    private static Line line(ContributionService.Line l) {
        return new Line(l.name(), l.symbol(), l.portfolioId(), l.gainEur(), l.returnPct());
    }

    private static double num(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }

    private static double nz(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

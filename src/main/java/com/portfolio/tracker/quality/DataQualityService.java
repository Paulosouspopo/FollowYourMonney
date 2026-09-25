package com.portfolio.tracker.quality;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.exchangerate.FxSymbols;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.YahooFinanceClient;
import com.portfolio.tracker.notification.Formats;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.quality.dto.DataIssue;
import com.portfolio.tracker.quality.dto.DataWarning;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Détection des erreurs de saisie : prix très éloigné du cours de clôture du
 * jour, actif probablement non éligible au PEA. Rien n'est bloqué ; un
 * contrôle peut être ignoré (« c'est normal »).
 *
 * <ul>
 * <li>{@link #checkTransaction} : pendant la saisie (peut télécharger l'historique d'un actif) ;</li>
 * <li>{@link #audit} : sur tout ce qui est déjà saisi, en mémoire, sans appel réseau.</li>
 * </ul>
 */
@Service
@Slf4j
public class DataQualityService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final DataCheckDismissalRepository dismissalRepository;
    private final PriceHistoryService priceHistoryService;
    private final MarketDataProvider marketDataProvider;
    private final ExchangeRateService exchangeRateService;
    private final TransactionTemplate tx;

    public DataQualityService(TransactionRepository transactionRepository, PortfolioRepository portfolioRepository,
            DataCheckDismissalRepository dismissalRepository, PriceHistoryService priceHistoryService,
            MarketDataProvider marketDataProvider, ExchangeRateService exchangeRateService,
            PlatformTransactionManager transactionManager) {
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.dismissalRepository = dismissalRepository;
        this.priceHistoryService = priceHistoryService;
        this.marketDataProvider = marketDataProvider;
        this.exchangeRateService = exchangeRateService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ saisie

    /** Contrôles d'une opération en cours de saisie (achat ou vente). Ne lève jamais pour un cours introuvable. */
    public List<DataWarning> checkTransaction(UUID userId, UUID portfolioId, String symbol, TransactionType type,
            LocalDate date, BigDecimal price, String currency) {
        Portfolio portfolio = tx.execute(s -> portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible")));
        Optional<MarketQuote> quote = marketDataProvider.getQuote(symbol);
        AssetType assetType = quote.map(q -> YahooFinanceClient.mapAssetType(q.instrumentType())).orElse(AssetType.AUTRE);
        List<DataWarning> warnings = new ArrayList<>();

        if (portfolio.getType() == PortfolioType.PEA) {
            QualityRules.peaProblem(symbol, assetType)
                    .ifPresent(m -> warnings.add(new DataWarning("PEA_INELIGIBLE", m, null, null, null)));
        }
        if (type != TransactionType.DIVIDEND && price != null && price.signum() > 0 && date != null && quote.isPresent()) {
            try {
                marketClose(symbol, date, quote.get()).ifPresent(close -> {
                    BigDecimal market = close.price().multiply(
                            exchangeRateService.getRateAsOf(close.currency(), currency, close.date()));
                    Optional<BigDecimal> split = QualityRules.splitFactor(price, market);
                    if (split.isPresent()) {
                        warnings.add(new DataWarning("SPLIT_SUSPECTED", splitMessage(symbol, split.get(), close.date(),
                                Formats.money(market.setScale(2, RoundingMode.HALF_UP), currency)), null, currency, close.date()));
                        return;
                    }
                    BigDecimal deviation = QualityRules.deviation(price, market);
                    if (QualityRules.isSuspicious(deviation, assetType)) {
                        BigDecimal suggested = market.setScale(market.compareTo(BigDecimal.ONE) < 0 ? 6 : 2, RoundingMode.HALF_UP);
                        warnings.add(new DataWarning("PRICE_MISMATCH",
                                "Cours de clôture du " + close.date().format(DAY) + " : " + Formats.money(suggested, currency)
                                        + ". Ton prix s'en écarte de " + Formats.signedPercent(deviation.movePointRight(2)) + ".",
                                suggested, currency, close.date()));
                    }
                });
            } catch (RuntimeException e) {
                log.debug("Contrôle du prix de {} impossible : {}", symbol, e.getMessage());
            }
        }
        return warnings;
    }

    /** Clôture du jour (ou du dernier jour de bourse avant), cotation courante pour aujourd'hui. */
    private Optional<DailyPrice> marketClose(String symbol, LocalDate date, MarketQuote quote) {
        if (!date.isBefore(LocalDate.now())) {
            return Optional.of(new DailyPrice(quote.marketDate(), quote.price(), quote.currency()));
        }
        priceHistoryService.ensureCoverage(symbol, date.minusDays(7));
        return priceHistoryService.findOnOrBefore(symbol, date)
                .filter(p -> !p.date().isBefore(date.minusDays(7))); // trop ancien : pas comparable
    }

    // ------------------------------------------------------------------- audit

    /** Incohérences de tout ce qui est déjà saisi (hors contrôles ignorés). En mémoire, sans réseau. */
    public List<DataIssue> audit(UUID userId) {
        return Objects.requireNonNull(tx.execute(s -> {
            Set<String> dismissed = dismissalRepository.findKeysByUserId(userId);
            List<Transaction> txs = transactionRepository.findAllForValuation(userId, null, null);
            List<DataIssue> issues = new ArrayList<>();
            peaIssues(txs, issues);
            priceIssues(txs, issues);
            return issues.stream().filter(i -> !dismissed.contains(i.key()))
                    .sorted(Comparator.comparing(DataIssue::date, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }));
    }

    private static void peaIssues(List<Transaction> txs, List<DataIssue> out) {
        Map<UUID, Transaction> firstByAsset = new LinkedHashMap<>();
        txs.forEach(t -> firstByAsset.putIfAbsent(t.getAsset().getId(), t));
        for (Transaction t : firstByAsset.values()) {
            Portfolio p = t.getAsset().getPortfolio();
            if (p.getType() != PortfolioType.PEA) {
                continue;
            }
            QualityRules.peaProblem(t.getAsset().getSymbol(), t.getAsset().getAssetType()).ifPresent(m ->
                    out.add(new DataIssue("PEA:" + t.getAsset().getId(), "PEA_INELIGIBLE", p.getId(), p.getName(), null,
                            t.getAsset().getSymbol(), null, t.getAsset().getSymbol() + " dans « " + p.getName() + " » : " + m,
                            null, null)));
        }
    }

    private void priceIssues(List<Transaction> txs, List<DataIssue> out) {
        List<Transaction> trades = txs.stream().filter(t -> t.getType() != TransactionType.DIVIDEND).toList();
        if (trades.isEmpty()) {
            return;
        }
        LocalDate from = trades.stream().map(t -> t.getTransactionDate().toLocalDate()).min(Comparator.naturalOrder())
                .orElseThrow().minusDays(7);
        LocalDate today = LocalDate.now();
        Set<String> symbols = trades.stream().map(t -> t.getAsset().getSymbol()).collect(Collectors.toSet());
        Map<String, NavigableMap<LocalDate, DailyPrice>> prices = priceHistoryService.loadSeries(symbols, from, today);

        // Paires de devises des cours (vers l'EUR), chargées une fois
        Set<String> pairs = prices.values().stream().flatMap(s -> s.values().stream()).map(DailyPrice::currency)
                .filter(c -> c != null && !c.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY))
                .map(c -> FxSymbols.pair(c, MoneyConstants.BASE_CURRENCY)).collect(Collectors.toSet());
        Map<String, NavigableMap<LocalDate, DailyPrice>> fx = pairs.isEmpty() ? Map.of()
                : priceHistoryService.loadSeries(pairs, from, today);

        Map<UUID, List<BigDecimal>> splitsByAsset = new HashMap<>();
        for (Transaction t : trades) {
            LocalDate day = t.getTransactionDate().toLocalDate();
            NavigableMap<LocalDate, DailyPrice> series = prices.get(t.getAsset().getSymbol());
            Map.Entry<LocalDate, DailyPrice> close = series == null ? null : series.floorEntry(day);
            if (close == null || close.getKey().isBefore(day.minusDays(7)) || t.getPricePerUnit() == null) {
                continue;
            }
            BigDecimal closeEur = toEur(close.getValue(), fx, day);
            BigDecimal rate = t.getExchangeRateToEur() != null ? t.getExchangeRateToEur() : BigDecimal.ONE;
            BigDecimal priceEur = t.getPricePerUnit().multiply(rate);
            if (closeEur == null) {
                continue;
            }
            Portfolio portfolio = t.getAsset().getPortfolio();
            Optional<BigDecimal> split = QualityRules.splitFactor(priceEur, closeEur);
            if (split.isPresent()) {
                // Même actif, écart comparable (±25 %) : même cause (division), une seule alerte.
                // Un écart différent (faute de frappe isolée) reste signalé à part.
                List<BigDecimal> seen = splitsByAsset.computeIfAbsent(t.getAsset().getId(), id -> new ArrayList<>());
                boolean similar = seen.stream().anyMatch(f -> split.get().divide(f, 4, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.25")) <= 0);
                if (!similar) {
                    seen.add(split.get());
                    out.add(new DataIssue("SPLIT:" + t.getId(), "SPLIT_SUSPECTED", portfolio.getId(), portfolio.getName(),
                            t.getId(), t.getAsset().getSymbol(), day,
                            splitMessage(t.getAsset().getSymbol(), split.get(), day, Formats.eur(closeEur)),
                            null, t.getCurrency()));
                }
                continue;
            }
            BigDecimal deviation = QualityRules.deviation(priceEur, closeEur);
            if (!QualityRules.isSuspicious(deviation, t.getAsset().getAssetType())) {
                continue;
            }
            BigDecimal suggested = rate.signum() > 0
                    ? closeEur.divide(rate, closeEur.compareTo(BigDecimal.ONE) < 0 ? 6 : 2, RoundingMode.HALF_UP) : null;
            String kind = t.getType() == TransactionType.BUY ? "Achat" : "Vente";
            out.add(new DataIssue("PRICE:" + t.getId(), "PRICE_MISMATCH", portfolio.getId(), portfolio.getName(), t.getId(),
                    t.getAsset().getSymbol(), day,
                    kind + " de " + t.getAsset().getSymbol() + " du " + day.format(DAY) + " à "
                            + Formats.money(t.getPricePerUnit(), t.getCurrency()) + " : la clôture était "
                            + (suggested != null ? Formats.money(suggested, t.getCurrency()) : Formats.eur(closeEur))
                            + " (" + Formats.signedPercent(deviation.movePointRight(2)) + ").",
                    suggested, t.getCurrency()));
        }
    }

    /** Écart trop grand pour une faute de frappe : division (ou regroupement) d'actions probable. */
    private static String splitMessage(String symbol, BigDecimal factor, LocalDate day, String marketPrice) {
        String ratio = factor.compareTo(BigDecimal.ONE) >= 0
                ? "environ " + factor.stripTrailingZeros().toPlainString() + " fois le cours de marché"
                : "environ " + BigDecimal.ONE.divide(factor, 0, RoundingMode.HALF_UP).toPlainString() + " fois moins que le cours de marché";
        return symbol + " : prix de " + ratio + " (" + marketPrice + " le " + day.format(DAY) + "). Faute de frappe"
                + " (un zéro en trop ?) ou division d'actions : dans ce cas la série de marché a été corrigée, pas ton"
                + " relevé ; rien à faire si la position est soldée, sinon ajuste quantités et prix d'après ton courtier.";
    }

    private static BigDecimal toEur(DailyPrice price, Map<String, NavigableMap<LocalDate, DailyPrice>> fx, LocalDate day) {
        String currency = price.currency();
        if (currency == null || currency.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY)) {
            return price.price();
        }
        NavigableMap<LocalDate, DailyPrice> series = fx.get(FxSymbols.pair(currency, MoneyConstants.BASE_CURRENCY));
        Map.Entry<LocalDate, DailyPrice> rate = series == null ? null : series.floorEntry(day);
        return rate == null ? null : price.price().multiply(rate.getValue().price());
    }

    // --------------------------------------------------------------- « normal »

    public void dismiss(UUID userId, String key) {
        tx.executeWithoutResult(s -> dismissalRepository.save(new DataCheckDismissal(userId, key)));
    }
}

package com.portfolio.tracker.tax;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.assetprice.PriceHistoryService;
import com.portfolio.tracker.assetprice.dto.DailyPrice;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.exchangerate.FxSymbols;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.tax.dto.TaxReport;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Récapitulatif fiscal annuel : valeurs mobilières (hors PEA), crypto-actifs
 * (150 VH bis), état des PEA. Tout est calculé à partir des opérations
 * saisies, en mémoire (cours et taux déjà en base, aucun appel réseau).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxService {

    static final BigDecimal FLAT_TAX = new BigDecimal("0.30");
    static final BigDecimal SOCIAL_CHARGES = new BigDecimal("0.172");
    static final BigDecimal CRYPTO_THRESHOLD = new BigDecimal("305");
    static final BigDecimal PEA_CEILING = new BigDecimal("150000");

    private final TransactionRepository transactionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioValuationService valuationService;
    private final PriceHistoryService priceHistoryService;

    public TaxReport report(UUID userId, Integer requestedYear) {
        List<Transaction> all = transactionRepository.findAllForValuation(userId, null, null);
        List<Transaction> securities = all.stream().filter(TaxService::isSecurity).toList();
        List<Transaction> crypto = all.stream().filter(TaxService::isCrypto).toList();

        // Années disponibles : cessions et dividendes imposables, plus l'année en cours
        int currentYear = LocalDate.now().getYear();
        SortedSet<Integer> years = Stream.concat(securities.stream(), crypto.stream())
                .filter(t -> t.getType() != TransactionType.BUY)
                .map(t -> t.getTransactionDate().getYear())
                .collect(Collectors.toCollection(TreeSet::new));
        years.add(currentYear);
        int year = requestedYear != null ? requestedYear
                : years.contains(currentYear - 1) ? currentYear - 1 : currentYear;

        return new TaxReport(year, new ArrayList<>(years).reversed(), securities(securities, year),
                crypto(crypto, year), peas(userId, all), reminders());
    }

    // ------------------------------------------------------------ titres

    private TaxReport.Securities securities(List<Transaction> txs, int year) {
        List<TaxEngine.SecuritySale> allSales = TaxEngine.securitySales(txs);
        TreeMap<Integer, BigDecimal> netByYear = allSales.stream().collect(Collectors.groupingBy(
                s -> s.date().getYear(), TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, TaxEngine.SecuritySale::gainEur, BigDecimal::add)));
        List<TaxEngine.SecuritySale> sales = allSales.stream().filter(s -> s.date().getYear() == year).toList();

        BigDecimal gains = sum(sales.stream().map(TaxEngine.SecuritySale::gainEur).filter(g -> g.signum() > 0));
        BigDecimal losses = sum(sales.stream().map(TaxEngine.SecuritySale::gainEur).filter(g -> g.signum() < 0));
        BigDecimal net = gains.add(losses);
        BigDecimal[] carry = TaxEngine.carryForward(netByYear, year);
        BigDecimal dividends = sum(txs.stream()
                .filter(t -> t.getType() == TransactionType.DIVIDEND && t.getTransactionDate().getYear() == year)
                .map(t -> TaxEngine.nz(t.getTotalAmountEur())));
        BigDecimal taxable = carry[1];
        BigDecimal tax = taxable.add(dividends).multiply(FLAT_TAX);

        List<TaxReport.Box> boxes = new ArrayList<>();
        if (net.signum() > 0) {
            boxes.add(new TaxReport.Box("3VG", "Plus-value nette imposable (après moins-values reportées)", money(taxable), "2042 / 2074"));
        } else if (net.signum() < 0) {
            boxes.add(new TaxReport.Box("3VH", "Moins-value de l'année (reportable 10 ans)", money(net.negate()), "2042 / 2074"));
        }
        if (dividends.signum() > 0) {
            boxes.add(new TaxReport.Box("2DC", "Dividendes (montant brut)", money(dividends), "2042"));
        }
        return new TaxReport.Securities(
                sales.stream().map(s -> new TaxReport.Sale(s.date(), s.symbol(), s.name(), s.quantity(), s.proceedsEur(),
                        s.costEur(), s.gainEur())).toList(),
                money(gains), money(losses), money(net), carry[0], taxable, carry[2], money(dividends), money(tax), boxes);
    }

    // ------------------------------------------------------------ crypto

    private TaxReport.Crypto crypto(List<Transaction> txs, int year) {
        List<TaxEngine.CryptoSale> allSales = TaxEngine.cryptoSales(txs, cryptoPrices(txs));
        List<TaxEngine.CryptoSale> sales = allSales.stream().filter(s -> s.date().getYear() == year).toList();
        BigDecimal proceeds = sum(sales.stream().map(TaxEngine.CryptoSale::proceedsEur));
        BigDecimal net = sum(sales.stream().map(TaxEngine.CryptoSale::gainEur));
        boolean exempt = proceeds.compareTo(CRYPTO_THRESHOLD) <= 0;
        BigDecimal tax = exempt || net.signum() <= 0 ? BigDecimal.ZERO : net.multiply(FLAT_TAX);

        List<TaxReport.Box> boxes = new ArrayList<>();
        if (!sales.isEmpty() && !exempt) {
            boxes.add(net.signum() >= 0
                    ? new TaxReport.Box("3AN", "Plus-value sur actifs numériques", money(net), "2042 / 2086")
                    : new TaxReport.Box("3BN", "Moins-value sur actifs numériques (non reportable)", money(net.negate()), "2042 / 2086"));
        }
        return new TaxReport.Crypto(
                sales.stream().map(s -> new TaxReport.CryptoSale(s.date(), s.symbol(), s.proceedsEur(), s.portfolioValueEur(),
                        s.acquisitionShareEur(), s.gainEur())).toList(),
                money(proceeds), money(net), exempt, money(tax), boxes);
    }

    /** Cours EUR de clôture (dernier connu) d'un crypto-actif à une date : séries en mémoire, conversions comprises. */
    private java.util.function.BiFunction<String, LocalDate, BigDecimal> cryptoPrices(List<Transaction> txs) {
        if (txs.isEmpty()) {
            return (s, d) -> null;
        }
        LocalDate from = txs.get(0).getTransactionDate().toLocalDate().minusDays(7);
        LocalDate to = LocalDate.now();
        Set<String> symbols = txs.stream().map(t -> t.getAsset().getSymbol()).collect(Collectors.toSet());
        Map<String, NavigableMap<LocalDate, DailyPrice>> prices = priceHistoryService.loadSeries(symbols, from, to);
        Set<String> pairs = prices.values().stream().flatMap(s -> s.values().stream()).map(DailyPrice::currency)
                .filter(c -> c != null && !c.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY))
                .map(c -> FxSymbols.pair(c, MoneyConstants.BASE_CURRENCY)).collect(Collectors.toSet());
        Map<String, NavigableMap<LocalDate, DailyPrice>> fx = pairs.isEmpty() ? Map.of()
                : priceHistoryService.loadSeries(pairs, from, to);
        return (symbol, day) -> {
            NavigableMap<LocalDate, DailyPrice> series = prices.get(symbol);
            Map.Entry<LocalDate, DailyPrice> p = series == null ? null : series.floorEntry(day);
            if (p == null) {
                return null;
            }
            String currency = p.getValue().currency();
            if (currency == null || currency.equalsIgnoreCase(MoneyConstants.BASE_CURRENCY)) {
                return p.getValue().price();
            }
            NavigableMap<LocalDate, DailyPrice> rates = fx.get(FxSymbols.pair(currency, MoneyConstants.BASE_CURRENCY));
            Map.Entry<LocalDate, DailyPrice> r = rates == null ? null : rates.floorEntry(day);
            return r == null ? null : p.getValue().price().multiply(r.getValue().price());
        };
    }

    // ------------------------------------------------------------ PEA

    private List<TaxReport.PeaStatus> peas(UUID userId, List<Transaction> all) {
        List<Portfolio> peas = portfolioRepository.findByUserId(userId).stream()
                .filter(p -> p.getType() == PortfolioType.PEA).toList();
        if (peas.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> values = valuationService.valuate(userId, null, null).getPortfolios().stream()
                .collect(Collectors.toMap(PortfolioValuation::getPortfolioId, PortfolioValuation::getCurrentValueEur));
        List<CashMovement> movements = cashMovementRepository.findAllForValuation(userId, null);
        LocalDate today = LocalDate.now();

        List<TaxReport.PeaStatus> out = new ArrayList<>();
        for (Portfolio p : peas) {
            List<Transaction> txs = all.stream().filter(t -> t.getAsset().getPortfolio().getId().equals(p.getId())).toList();
            List<CashMovement> own = movements.stream().filter(m -> m.getPortfolio().getId().equals(p.getId())).toList();
            LocalDate firstEvent = Stream.concat(txs.stream().map(t -> t.getTransactionDate().toLocalDate()),
                    own.stream().map(CashMovement::getMovementDate)).min(Comparator.naturalOrder()).orElse(null);
            LocalDate opened = p.getOpenedAt() != null ? p.getOpenedAt() : firstEvent;
            LocalDate five = opened != null ? opened.plusYears(5) : null;

            BigDecimal deposits;
            boolean estimated = !p.isCashTracking();
            if (!estimated) {
                deposits = sum(own.stream().filter(m -> m.getType() == CashMovementType.DEPOSIT).map(CashMovement::getAmount))
                        .subtract(sum(own.stream().filter(m -> m.getType() == CashMovementType.WITHDRAWAL).map(CashMovement::getAmount)));
            } else {
                // Sans suivi des liquidités : argent mis en jeu = achats (frais compris) - ventes nettes
                deposits = sum(txs.stream().map(t -> switch (t.getType()) {
                    case BUY -> TaxEngine.nz(t.getTotalAmountEur()).add(TaxEngine.nz(t.getFeesEur()));
                    case SELL -> TaxEngine.nz(t.getTotalAmountEur()).subtract(TaxEngine.nz(t.getFeesEur())).negate();
                    case DIVIDEND -> BigDecimal.ZERO;
                })).max(BigDecimal.ZERO);
            }
            BigDecimal value = values.getOrDefault(p.getId(), BigDecimal.ZERO);
            BigDecimal gain = value.subtract(deposits).max(BigDecimal.ZERO);
            out.add(new TaxReport.PeaStatus(p.getId(), p.getName(), opened, p.getOpenedAt() == null, five,
                    five != null && !five.isAfter(today), money(deposits), estimated, PEA_CEILING, money(value),
                    money(gain.multiply(SOCIAL_CHARGES))));
        }
        return out;
    }

    // ------------------------------------------------------------ utils

    private static List<String> reminders() {
        return List.of(
                "Estimation établie d'après tes opérations saisies ou importées : vérifie-la avec l'IFU de chaque courtier (formulaire 2561), qui fait foi.",
                "Flat tax par défaut (12,8 % d'impôt + 17,2 % de prélèvements sociaux). L'option pour le barème progressif (case 2OP) peut être plus avantageuse selon ta tranche.",
                "Comptes ouverts à l'étranger (Binance, Coinbase, Revolut, certains néo-courtiers…) : à déclarer chaque année (formulaire 3916 / 3916-bis), même inactifs.",
                "Dividendes étrangers : la retenue à la source ouvre parfois droit à un crédit d'impôt, non calculé ici.");
    }

    private static boolean isSecurity(Transaction t) {
        PortfolioType type = t.getAsset().getPortfolio().getType();
        return type != PortfolioType.PEA && type != PortfolioType.LIVRET && t.getAsset().getAssetType() != AssetType.CRYPTO;
    }

    private static boolean isCrypto(Transaction t) {
        return t.getAsset().getPortfolio().getType() != PortfolioType.PEA && t.getAsset().getAssetType() == AssetType.CRYPTO;
    }

    private static BigDecimal sum(Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}

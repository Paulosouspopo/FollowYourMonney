package com.portfolio.tracker.tax;

import com.portfolio.tracker.dashboard.PositionState;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Calculs fiscaux français, sans base ni réseau (testés à part). Estimations
 * pour aider à remplir la déclaration, pas un conseil fiscal.
 *
 * <ul>
 * <li><b>Valeurs mobilières</b> (hors PEA) : plus-value = prix de cession net
 * de frais - prix moyen pondéré d'acquisition frais compris, calculé par titre
 * sur l'ensemble des comptes (règle du PMP, identique au CUMP de l'app).</li>
 * <li><b>Crypto-actifs</b> (article 150 VH bis du CGI) : à chaque cession
 * contre euros, plus-value = C - PTA × C / V, où C est le prix de cession
 * net de frais, V la valeur globale du portefeuille crypto juste avant, PTA le
 * prix total d'acquisition restant (diminué de la part imputée à chaque
 * cession). Les échanges crypto contre crypto ne sont pas imposables.</li>
 * </ul>
 */
public final class TaxEngine {

    /** Deux opérations au plus aussi éloignées (et de montants voisins) forment un échange crypto contre crypto. */
    static final Duration SWAP_WINDOW = Duration.ofMinutes(2);
    static final BigDecimal SWAP_AMOUNT_TOLERANCE = new BigDecimal("0.05");

    private TaxEngine() {
    }

    // ------------------------------------------------------------ titres

    /** Cession d'un titre : quantité, prix de cession net, coût d'acquisition (PMP), plus-value. */
    public record SecuritySale(LocalDate date, String symbol, String name, BigDecimal quantity,
                               BigDecimal proceedsEur, BigDecimal costEur, BigDecimal gainEur) {
    }

    /**
     * Cessions de titres, PMP calculé par symbole tous comptes confondus.
     *
     * @param txs opérations hors PEA sur des titres (actions, ETF…), dans l'ordre chronologique
     */
    public static List<SecuritySale> securitySales(List<Transaction> txs) {
        Map<String, PositionState> bySymbol = new HashMap<>();
        List<SecuritySale> sales = new ArrayList<>();
        for (Transaction t : txs) {
            PositionState state = bySymbol.computeIfAbsent(t.getAsset().getSymbol(), s -> new PositionState());
            if (t.getType() != TransactionType.SELL) {
                state.apply(t);
                continue;
            }
            BigDecimal realizedBefore = state.getRealizedEur();
            BigDecimal quantity = t.getQuantity().min(state.getQuantity());
            state.apply(t);
            BigDecimal gain = state.getRealizedEur().subtract(realizedBefore);
            BigDecimal proceeds = nz(t.getTotalAmountEur()).subtract(nz(t.getFeesEur()));
            sales.add(new SecuritySale(t.getTransactionDate().toLocalDate(), t.getAsset().getSymbol(), t.getAsset().getName(),
                    quantity, money(proceeds), money(proceeds.subtract(gain)), money(gain)));
        }
        return sales;
    }

    /**
     * Report des moins-values (valeurs mobilières) : une moins-value nette
     * s'impute sur les plus-values des 10 années suivantes, les plus anciennes
     * d'abord.
     *
     * @param netByYear plus-value nette de chaque année (négative = moins-value)
     * @return pour {@code year} : {moins-values reportées imputées, plus-value imposable, moins-values reportables après}
     */
    public static BigDecimal[] carryForward(SortedMap<Integer, BigDecimal> netByYear, int year) {
        Deque<BigDecimal[]> pool = new ArrayDeque<>(); // {année d'origine, montant restant}
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        for (Map.Entry<Integer, BigDecimal> e : netByYear.headMap(year + 1).entrySet()) {
            int y = e.getKey();
            pool.removeIf(p -> p[0].intValue() < y - 10);
            BigDecimal net = e.getValue();
            BigDecimal usedThisYear = BigDecimal.ZERO;
            if (net.signum() < 0) {
                pool.addLast(new BigDecimal[] { BigDecimal.valueOf(y), net.negate() });
            } else {
                BigDecimal remaining = net;
                for (BigDecimal[] p : pool) {
                    if (remaining.signum() <= 0) {
                        break;
                    }
                    BigDecimal take = p[1].min(remaining);
                    p[1] = p[1].subtract(take);
                    remaining = remaining.subtract(take);
                    usedThisYear = usedThisYear.add(take);
                }
                pool.removeIf(p -> p[1].signum() == 0);
                if (y == year) {
                    taxable = remaining;
                }
            }
            if (y == year) {
                used = usedThisYear;
            }
        }
        BigDecimal left = pool.stream().filter(p -> p[0].intValue() >= year - 9)
                .map(p -> p[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BigDecimal[] { money(used), money(taxable), money(left) };
    }

    // ------------------------------------------------------------ crypto

    /**
     * Cession imposable de crypto-actifs (150 VH bis).
     *
     * @param portfolioValueEur valeur globale du portefeuille crypto juste avant la cession (V)
     * @param acquisitionShareEur part du prix total d'acquisition imputée (PTA × C / V)
     */
    public record CryptoSale(LocalDate date, String symbol, BigDecimal proceedsEur, BigDecimal portfolioValueEur,
                             BigDecimal acquisitionShareEur, BigDecimal gainEur) {
    }

    /**
     * @param txs   opérations crypto de tous les comptes, dans l'ordre chronologique
     * @param price cours EUR d'un symbole à une date (null si inconnu : dernier prix d'opération)
     */
    public static List<CryptoSale> cryptoSales(List<Transaction> txs, BiFunction<String, LocalDate, BigDecimal> price) {
        Set<UUID> swaps = swapLegs(txs);
        Map<String, BigDecimal> held = new HashMap<>();
        Map<String, BigDecimal> lastPrice = new HashMap<>();
        BigDecimal pta = BigDecimal.ZERO;
        List<CryptoSale> sales = new ArrayList<>();

        for (Transaction t : txs) {
            String symbol = t.getAsset().getSymbol();
            if (t.getQuantity() != null && t.getQuantity().signum() > 0 && t.getTotalAmountEur() != null) {
                lastPrice.put(symbol, t.getTotalAmountEur().divide(t.getQuantity(), 8, RoundingMode.HALF_UP));
            }
            switch (t.getType()) {
                case BUY -> {
                    held.merge(symbol, t.getQuantity(), BigDecimal::add);
                    if (!swaps.contains(t.getId())) {
                        pta = pta.add(nz(t.getTotalAmountEur())).add(nz(t.getFeesEur()));
                    }
                }
                case SELL -> {
                    if (!swaps.contains(t.getId())) {
                        LocalDate day = t.getTransactionDate().toLocalDate();
                        BigDecimal proceeds = nz(t.getTotalAmountEur()).subtract(nz(t.getFeesEur())).max(BigDecimal.ZERO);
                        BigDecimal value = BigDecimal.ZERO;
                        for (Map.Entry<String, BigDecimal> h : held.entrySet()) {
                            if (h.getValue().signum() <= 0) {
                                continue;
                            }
                            BigDecimal p = price.apply(h.getKey(), day);
                            if (p == null) {
                                p = lastPrice.getOrDefault(h.getKey(), BigDecimal.ZERO);
                            }
                            value = value.add(h.getValue().multiply(p));
                        }
                        value = value.max(proceeds); // V ne peut être inférieure à ce qui est vendu
                        BigDecimal share = value.signum() > 0
                                ? pta.multiply(proceeds).divide(value, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                        pta = pta.subtract(share);
                        sales.add(new CryptoSale(day, symbol, money(proceeds), money(value), money(share),
                                money(proceeds.subtract(share))));
                    }
                    held.merge(symbol, t.getQuantity().negate(), BigDecimal::add);
                }
                case DIVIDEND -> {
                    // Récompenses (staking…) : hors périmètre de cette estimation
                }
            }
        }
        return sales;
    }

    /**
     * Jambes d'échanges crypto contre crypto : une vente et un achat du même
     * compte, à quelques minutes d'écart, pour des montants voisins (c'est
     * ainsi que l'import Binance représente une conversion).
     */
    static Set<UUID> swapLegs(List<Transaction> txs) {
        Set<UUID> legs = new HashSet<>();
        for (Transaction sell : txs) {
            if (sell.getType() != TransactionType.SELL || legs.contains(sell.getId())) {
                continue;
            }
            for (Transaction buy : txs) {
                if (buy.getType() != TransactionType.BUY || legs.contains(buy.getId())
                        || !buy.getAsset().getPortfolio().getId().equals(sell.getAsset().getPortfolio().getId())
                        || buy.getAsset().getSymbol().equals(sell.getAsset().getSymbol())) {
                    continue;
                }
                Duration gap = Duration.between(sell.getTransactionDate(), buy.getTransactionDate()).abs();
                BigDecimal a = nz(sell.getTotalAmountEur());
                BigDecimal b = nz(buy.getTotalAmountEur());
                BigDecimal max = a.max(b);
                if (gap.compareTo(SWAP_WINDOW) <= 0 && max.signum() > 0
                        && a.subtract(b).abs().divide(max, 4, RoundingMode.HALF_UP).compareTo(SWAP_AMOUNT_TOLERANCE) <= 0) {
                    legs.add(sell.getId());
                    legs.add(buy.getId());
                    break;
                }
            }
        }
        return legs;
    }

    // ------------------------------------------------------------ utils

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.imports.AssetRef;
import com.portfolio.tracker.imports.ImportFormat;
import com.portfolio.tracker.imports.ImportKind;
import com.portfolio.tracker.imports.ImportedOperation;
import com.portfolio.tracker.imports.csv.CsvTable;
import com.portfolio.tracker.imports.csv.Numbers;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Binance — « Transaction History » (historique du compte).
 *
 * <pre>User ID,Time,Account,Operation,Coin,Change,Remark</pre>
 *
 * Ce n'est pas une liste d'opérations mais un grand livre : une ligne par
 * variation de solde. Un achat de BTC contre 125 EUR = deux lignes (-125 EUR,
 * +0.00225 BTC) à la même seconde (ou presque). Le parser regroupe ces
 * « jambes » puis en déduit l'opération :
 * <ul>
 * <li>EUR → crypto : achat au prix = euros dépensés / quantité reçue ;</li>
 * <li>crypto → EUR : vente ;</li>
 * <li>crypto → crypto : vente + achat, valorisés au cours du jour (le fichier
 * ne donne pas de valeur en euros) ;</li>
 * <li>Deposit / Withdraw EUR : versement / retrait ;</li>
 * <li>récompenses (Crypto Box, airdrops...) : non importées pour l'instant, de
 * même que la conversion ultérieure de ces poussières.</li>
 * </ul>
 * Les frais prélevés dans une crypto (« Transaction Fee ») sont déduits de la
 * quantité reçue : la quantité importée est celle réellement détenue.
 */
@Component
public class BinanceParser implements StatementParser {

    private static final String FIAT = "EUR";
    /** Écart maximal entre deux jambes d'une même opération. */
    private static final Duration LEG_WINDOW = Duration.ofSeconds(2);
    private static final Set<String> REWARD_OPERATIONS = Set.of(
            "crypto box", "distribution", "airdrop assets", "simple earn flexible interest",
            "simple earn locked rewards", "staking rewards", "referral commission", "commission rebate");

    @Override
    public ImportFormat format() {
        return ImportFormat.BINANCE;
    }

    @Override
    public boolean supports(CsvTable table) {
        return table.hasColumns("Time", "Account", "Operation", "Coin", "Change");
    }

    /** Ligne lue du grand livre. */
    private record Leg(int line, LocalDateTime time, String account, String operation, String coin,
                       BigDecimal change, String remark, String raw) {
    }

    @Override
    public List<ImportedOperation> parse(CsvTable t) {
        List<ImportedOperation> result = new ArrayList<>();
        List<Leg> legs = new ArrayList<>();
        int time = t.column("Time"), account = t.column("Account"), operation = t.column("Operation"),
                coin = t.column("Coin"), change = t.column("Change"), remark = t.column("Remark");

        for (CsvTable.Line line : t.lines()) {
            Optional<LocalDateTime> when = DateParsing.parse(line.cell(time));
            Optional<BigDecimal> amount = Numbers.parse(line.cell(change));
            if (when.isEmpty() || amount.isEmpty()) {
                result.add(ImportedOperation.ignored(List.of(line.number()), when.orElse(null), "Ligne illisible"));
                continue;
            }
            legs.add(new Leg(line.number(), when.get(), line.cell(account), line.cell(operation),
                    line.cell(coin).toUpperCase(), amount.get(), line.cell(remark), line.raw()));
        }
        legs.sort((a, b) -> a.time().compareTo(b.time()) != 0 ? a.time().compareTo(b.time()) : Integer.compare(a.line(), b.line()));

        ExternalRefs refs = new ExternalRefs("BINANCE");
        Set<String> rewardOnly = new HashSet<>();
        Set<String> bought = new HashSet<>();

        for (List<Leg> group : group(legs)) {
            result.addAll(interpret(group, refs, rewardOnly, bought));
        }
        result.sort((a, b) -> compareNullable(a.getDateTime(), b.getDateTime()));
        return result;
    }

    // --------------------------------------------------------------- regroupement

    /**
     * Regroupe les jambes d'une même opération : même famille d'opération, même
     * compte, et même « Remark » (identifiant d'ordre) ou, à défaut, moins de
     * 2 secondes d'écart.
     */
    private List<List<Leg>> group(List<Leg> legs) {
        List<List<Leg>> groups = new ArrayList<>();
        List<Leg> current = new ArrayList<>();
        for (Leg leg : legs) {
            if (!current.isEmpty() && !belongsTo(current, leg)) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(leg);
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private boolean belongsTo(List<Leg> group, Leg leg) {
        Leg first = group.get(0);
        if (!family(first).equals(family(leg)) || !first.account().equals(leg.account()) || isSingleLeg(leg)) {
            return false;
        }
        if (!first.remark().isEmpty() && first.remark().equals(leg.remark())) {
            return true;
        }
        return Duration.between(first.time(), leg.time()).compareTo(LEG_WINDOW) <= 0;
    }

    private static String family(Leg leg) {
        String op = leg.operation().toLowerCase();
        return op.startsWith("transaction ") ? "transaction" : op;
    }

    private static boolean isSingleLeg(Leg leg) {
        String op = leg.operation().toLowerCase();
        return op.equals("deposit") || op.equals("withdraw") || REWARD_OPERATIONS.contains(op);
    }

    // ---------------------------------------------------------------- lecture

    private List<ImportedOperation> interpret(List<Leg> group, ExternalRefs refs, Set<String> rewardOnly,
                                              Set<String> bought) {
        Leg first = group.get(0);
        List<Integer> lines = group.stream().map(Leg::line).toList();
        LocalDateTime when = first.time();
        String op = first.operation().toLowerCase();
        String ref = refs.of(String.join("|", group.stream().map(Leg::raw).toList()));

        if (REWARD_OPERATIONS.contains(op)) {
            if (!bought.contains(first.coin())) {
                rewardOnly.add(first.coin());
            }
            return List.of(ImportedOperation.ignored(lines, when,
                    "Récompense (" + first.operation() + ", " + first.change().toPlainString() + " " + first.coin()
                            + ") : non importée"));
        }
        if (op.equals("deposit") || op.equals("withdraw")) {
            if (!first.coin().equals(FIAT)) {
                return List.of(ImportedOperation.ignored(lines, when, (op.equals("deposit") ? "Dépôt" : "Retrait")
                        + " de " + first.coin() + " depuis/vers un autre portefeuille : prix d'achat inconnu, "
                        + "à saisir manuellement"));
            }
            return List.of(ImportedOperation.builder()
                    .lines(lines).dateTime(when)
                    .kind(op.equals("deposit") ? ImportKind.DEPOSIT : ImportKind.WITHDRAWAL)
                    .amount(first.change().abs()).currency(FIAT)
                    .externalRef(ref)
                    .build());
        }

        // Solde net par actif : les frais (« Transaction Fee ») viennent en déduction
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        group.forEach(leg -> net.merge(leg.coin(), leg.change(), BigDecimal::add));
        net.values().removeIf(v -> v.signum() == 0);
        List<Map.Entry<String, BigDecimal>> from = net.entrySet().stream().filter(e -> e.getValue().signum() < 0).toList();
        List<Map.Entry<String, BigDecimal>> to = net.entrySet().stream().filter(e -> e.getValue().signum() > 0).toList();

        if (from.size() != 1 || to.size() != 1) {
            return List.of(ImportedOperation.ignored(lines, when,
                    "Opération « " + first.operation() + " » non reconnue (" + net + ")"));
        }
        String fromCoin = from.get(0).getKey();
        BigDecimal fromQty = from.get(0).getValue().abs();
        String toCoin = to.get(0).getKey();
        BigDecimal toQty = to.get(0).getValue();

        if (fromCoin.equals(FIAT)) {
            bought.add(toCoin);
            rewardOnly.remove(toCoin);
            return List.of(trade(ImportKind.BUY, toCoin, toQty, fromQty, lines, when, ref));
        }
        if (toCoin.equals(FIAT)) {
            return List.of(trade(ImportKind.SELL, fromCoin, fromQty, toQty, lines, when, ref));
        }

        // Crypto → crypto
        if (rewardOnly.contains(fromCoin)) {
            return List.of(ImportedOperation.ignored(lines, when, "Conversion d'une récompense non importée ("
                    + fromQty.toPlainString() + " " + fromCoin + " → " + toQty.toPlainString() + " " + toCoin
                    + ") : montant négligeable"));
        }
        bought.add(toCoin);
        rewardOnly.remove(toCoin);
        AssetRef valuation = AssetRef.crypto(fromCoin, fromCoin);
        String note = "Conversion " + fromCoin + " → " + toCoin + " (valeur estimée au cours du jour)";
        return List.of(
                estimated(ImportKind.SELL, fromCoin, fromQty, valuation, fromQty, lines, when, ref + ":SELL", note),
                estimated(ImportKind.BUY, toCoin, toQty, valuation, fromQty, lines, when, ref + ":BUY", note));
    }

    private static ImportedOperation trade(ImportKind kind, String coin, BigDecimal qty, BigDecimal eur,
                                           List<Integer> lines, LocalDateTime when, String ref) {
        return ImportedOperation.builder()
                .lines(lines).dateTime(when).kind(kind)
                .asset(AssetRef.crypto(coin, coin))
                .quantity(qty)
                // Frais de conversion inclus dans le prix (Binance ne les détaille pas)
                .unitPrice(eur.divide(qty, 8, RoundingMode.HALF_UP))
                .currency(FIAT)
                .externalRef(ref)
                .build();
    }

    private static ImportedOperation estimated(ImportKind kind, String coin, BigDecimal qty, AssetRef valuation,
                                               BigDecimal valuationQty, List<Integer> lines, LocalDateTime when,
                                               String ref, String note) {
        return ImportedOperation.builder()
                .lines(lines).dateTime(when).kind(kind)
                .asset(AssetRef.crypto(coin, coin))
                .quantity(qty)
                .currency(FIAT)
                .priceEstimated(true)
                .valuationAsset(valuation)
                .valuationQuantity(valuationQty)
                .notes(note)
                .externalRef(ref)
                .build();
    }

    private static int compareNullable(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        return a.compareTo(b);
    }
}

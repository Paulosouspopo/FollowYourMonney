package com.portfolio.tracker.analysis;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.marketdata.AssetProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Radiographie d'un patrimoine (calcul pur, testé) : où est réellement investi
 * l'argent, en regardant à l'intérieur des fonds.
 *
 * <ul>
 * <li>Classes d'actifs : actions, obligations, liquidités (y compris dans les
 * fonds), fonds euros, livrets, crypto.</li>
 * <li>Pays et secteurs : sur la partie actions seulement. Action : son pays et
 * son secteur (Yahoo). ETF / fonds : secteurs fournis par Yahoo, pays estimés
 * d'après l'indice cité dans son nom ({@link Geography}).</li>
 * <li>Devises : devise du pays pour les actions (exposition économique), sinon
 * devise de cotation ; liquidités dans leur devise.</li>
 * <li>Expositions réelles : actions détenues en direct + 10 premières lignes
 * des fonds, regroupées.</li>
 * <li>Frais : frais courants (TER) des fonds, Yahoo ou saisis par
 * l'utilisateur, et leur coût sur 20 ans.</li>
 * </ul>
 */
public final class ExposureCalculator {

    /** Rendement supposé pour chiffrer le coût des frais sur 20 ans. */
    static final double GROWTH = 0.05;
    static final int FEE_YEARS = 20;
    public static final String UNKNOWN_SECTOR = "unknown";
    public static final String CRYPTO = "CRYPTO";

    private ExposureCalculator() {
    }

    /** Ligne détenue. {@code feeOverridePct} : frais saisis par l'utilisateur (prioritaires). */
    public record Line(String symbol, String name, AssetType type, String tradingCurrency, double valueEur,
                       Double feeOverridePct) {
    }

    /** Liquidités d'un compte : LIQUIDITES, FONDS_EUROS ou LIVRETS, dans une devise. */
    public record Cash(String category, String currency, double valueEur) {
    }

    public record Slice(String key, String label, double valueEur, double pct) {
    }

    public record RealExposure(String name, String symbol, double valueEur, double pct, boolean viaFunds) {
    }

    public record FeeLine(String symbol, String name, double valueEur, Double terPct, boolean userProvided,
                          Double annualCostEur) {
    }

    /**
     * @param weightedTerPct   frais moyens pondérés des fonds dont on connaît les frais
     * @param unknownValueEur  montant investi dans des fonds aux frais inconnus
     * @param twentyYearCostEur ce que les frais connus coûteront sur 20 ans (rendement supposé 5 %/an)
     */
    public record Fees(double brokerFeesPaidEur, double fundsValueEur, double annualFundFeesEur, Double weightedTerPct,
                       double unknownValueEur, double twentyYearCostEur, List<FeeLine> lines) {
    }

    /**
     * @param equityEur          partie actions (base des pays et secteurs)
     * @param countryKnownPct    part des actions dont le pays est connu ou estimé
     * @param countryEstimatedPct part des actions dont le pays est estimé d'après un indice
     */
    public record Exposure(double totalEur, double equityEur, double countryKnownPct, double countryEstimatedPct,
                           List<Slice> classes, List<Slice> countries, List<Slice> sectors, List<Slice> currencies,
                           List<RealExposure> topExposures, String largestLineName, double largestLinePct, Fees fees) {
    }

    public static Exposure compute(List<Line> lines, List<Cash> cash, Map<String, AssetProfile> profiles,
            double brokerFeesPaidEur) {
        Map<String, Double> classes = new LinkedHashMap<>();
        Map<String, Double> countries = new HashMap<>();
        Map<String, Double> sectors = new HashMap<>();
        Map<String, Double> currencies = new HashMap<>();
        Map<String, double[]> real = new HashMap<>();          // clé → [valeur, viaFunds ? 1 : 0]
        Map<String, String[]> realNames = new HashMap<>();     // clé → [nom, symbole]
        List<FeeLine> feeLines = new ArrayList<>();
        double equity = 0, countryEstimated = 0, total = 0;
        String largestName = null;
        double largest = 0;

        for (Line l : lines) {
            double v = l.valueEur();
            if (v <= 0) {
                continue;
            }
            total += v;
            if (v > largest) {
                largest = v;
                largestName = l.name();
            }
            AssetProfile p = profiles.get(l.symbol());
            switch (l.type()) {
                case ACTION -> {
                    equity += v;
                    add(classes, "ACTIONS", v);
                    String country = p != null ? Geography.code(p.country()) : Geography.UNKNOWN;
                    add(countries, country, v);
                    add(sectors, p != null ? sectorKey(p.sector()) : UNKNOWN_SECTOR, v);
                    String currency = Geography.currency(country);
                    add(currencies, currency != null ? currency : iso(l.tradingCurrency()), v);
                    addReal(real, realNames, l.symbol(), displayName(l, p), v, false);
                }
                case ETF, FONDS -> {
                    double[] split = split(l.type(), p);
                    if (split == null) {
                        add(classes, "FONDS_NON_DETAILLE", v);
                        add(currencies, iso(l.tradingCurrency()), v);
                    } else {
                        double stock = v * split[0];
                        add(classes, "ACTIONS", stock);
                        add(classes, "OBLIGATIONS", v * split[1]);
                        add(classes, "LIQUIDITES", v * split[2]);
                        add(classes, "AUTRES", v * split[3]);
                        equity += stock;
                        Map<String, Double> estimated = Geography.estimateFromName(
                                p != null && p.longName() != null ? p.longName() + " " + l.name() : l.name());
                        if (estimated.isEmpty()) {
                            add(countries, Geography.UNKNOWN, stock);
                            add(currencies, iso(l.tradingCurrency()), stock);
                        } else {
                            countryEstimated += stock;
                            for (Map.Entry<String, Double> e : estimated.entrySet()) {
                                add(countries, e.getKey(), stock * e.getValue());
                                String c = Geography.currency(e.getKey());
                                add(currencies, c != null ? c : "AUTRES", stock * e.getValue());
                            }
                        }
                        Map<String, Double> weights = p != null ? p.sectorWeights() : Map.of();
                        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
                        if (sum <= 0) {
                            add(sectors, UNKNOWN_SECTOR, stock);
                        } else {
                            weights.forEach((k, w) -> add(sectors, sectorKey(k), stock * w / sum));
                        }
                        // La partie non actions reste dans la devise de cotation du fonds
                        add(currencies, iso(l.tradingCurrency()), v - stock);
                        if (p != null) {
                            for (AssetProfile.Holding h : p.holdings()) {
                                addReal(real, realNames, h.symbol(), h.name(), v * h.weight(), true);
                            }
                        }
                    }
                    Double ter = l.feeOverridePct() != null ? l.feeOverridePct() : p != null ? p.expenseRatioPct() : null;
                    feeLines.add(new FeeLine(l.symbol(), l.name(), round(v), ter, l.feeOverridePct() != null,
                            ter != null ? round(v * ter / 100) : null));
                }
                case CRYPTO -> {
                    add(classes, CRYPTO, v);
                    add(currencies, CRYPTO, v);
                }
                default -> {
                    add(classes, "AUTRES", v);
                    add(currencies, iso(l.tradingCurrency()), v);
                }
            }
        }
        for (Cash c : cash) {
            if (c.valueEur() <= 0) {
                continue;
            }
            total += c.valueEur();
            add(classes, c.category(), c.valueEur());
            add(currencies, iso(c.currency()), c.valueEur());
        }

        double countryUnknown = countries.getOrDefault(Geography.UNKNOWN, 0.0);
        return new Exposure(round(total), round(equity),
                equity > 0 ? pct((equity - countryUnknown) / equity) : 0,
                equity > 0 ? pct(countryEstimated / equity) : 0,
                slices(classes, total, k -> k), slices(countries, equity, k -> Geography.country(k).name()),
                slices(sectors, equity, k -> k), slices(currencies, total, k -> k),
                topExposures(real, realNames, total), largestName, total > 0 ? pct(largest / total) : 0,
                fees(feeLines, brokerFeesPaidEur));
    }

    /**
     * Répartition actions / obligations / liquidités / autres d'un fonds (somme 1),
     * ou null si inconnue (fonds non coté, fonds sans détail). Un ETF sans
     * détail est supposé 100 % actions.
     */
    static double[] split(AssetType type, AssetProfile p) {
        if (p != null) {
            double s = nz(p.stockPct()), b = nz(p.bondPct()), c = nz(p.cashPct()), o = nz(p.otherPct());
            double sum = s + b + c + o;
            if (sum > 0.01) {
                return new double[] { s / sum, b / sum, c / sum, o / sum };
            }
            if (!p.sectorWeights().isEmpty() || type == AssetType.ETF) {
                return new double[] { 1, 0, 0, 0 };
            }
        }
        return type == AssetType.ETF ? new double[] { 1, 0, 0, 0 } : null;
    }

    /** Clé de secteur commune aux actions (« Financial Services ») et aux fonds (« financial_services »). */
    static String sectorKey(String sector) {
        if (sector == null || sector.isBlank()) {
            return UNKNOWN_SECTOR;
        }
        String k = sector.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return k.equals("real_estate") ? "realestate" : k;
    }

    private static Fees fees(List<FeeLine> perPosition, double brokerFees) {
        // Un même fonds détenu dans plusieurs comptes : une seule ligne (les frais sont ceux du fonds)
        Map<String, FeeLine> bySymbol = new LinkedHashMap<>();
        for (FeeLine f : perPosition) {
            bySymbol.merge(f.symbol(), f, (a, b) -> new FeeLine(a.symbol(), a.name(), round(a.valueEur() + b.valueEur()),
                    a.terPct(), a.userProvided(), a.terPct() != null ? round((a.valueEur() + b.valueEur()) * a.terPct() / 100) : null));
        }
        List<FeeLine> lines = new ArrayList<>(bySymbol.values());
        double funds = 0, known = 0, annual = 0, twenty = 0;
        for (FeeLine f : lines) {
            funds += f.valueEur();
            if (f.terPct() != null) {
                known += f.valueEur();
                annual += f.valueEur() * f.terPct() / 100;
                twenty += f.valueEur() * (Math.pow(1 + GROWTH, FEE_YEARS) - Math.pow(1 + GROWTH - f.terPct() / 100, FEE_YEARS));
            }
        }
        List<FeeLine> sorted = lines.stream()
                .sorted(Comparator.comparing((FeeLine f) -> f.annualCostEur() == null ? -1 : f.annualCostEur()).reversed())
                .toList();
        return new Fees(round(brokerFees), round(funds), round(annual), known > 0 ? round3(annual / known * 100) : null,
                round(funds - known), round(twenty), sorted);
    }

    private static List<RealExposure> topExposures(Map<String, double[]> real, Map<String, String[]> names, double total) {
        return real.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[0]).reversed())
                .limit(10)
                .map(e -> new RealExposure(names.get(e.getKey())[0], names.get(e.getKey())[1], round(e.getValue()[0]),
                        total > 0 ? pct(e.getValue()[0] / total) : 0, e.getValue()[1] > 0))
                .toList();
    }

    private static void addReal(Map<String, double[]> real, Map<String, String[]> names, String symbol, String name,
            double value, boolean viaFunds) {
        String key = symbol != null && !symbol.isBlank() ? symbol.toUpperCase(Locale.ROOT) : normalizeName(name);
        double[] cur = real.computeIfAbsent(key, k -> new double[2]);
        cur[0] += value;
        if (viaFunds) {
            cur[1] = 1;
        }
        names.putIfAbsent(key, new String[] { name, symbol });
    }

    private static String normalizeName(String name) {
        return name == null ? "?" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "")
                .replaceAll("(inc|corp|corporation|sa|se|plc|ltd|ag|nv|class[a-z])$", "");
    }

    private static String displayName(Line l, AssetProfile p) {
        return l.name() != null ? l.name() : p != null ? p.longName() : l.symbol();
    }

    private static List<Slice> slices(Map<String, Double> values, double base,
            java.util.function.Function<String, String> label) {
        Set<String> keys = new HashSet<>(values.keySet());
        return keys.stream()
                .filter(k -> values.get(k) > 0.005)
                .map(k -> new Slice(k, label.apply(k), round(values.get(k)), base > 0 ? pct(values.get(k) / base) : 0))
                .sorted(Comparator.comparingDouble(Slice::valueEur).reversed())
                .toList();
    }

    private static void add(Map<String, Double> m, String key, double v) {
        if (v > 0) {
            m.merge(key, v, Double::sum);
        }
    }

    private static String iso(String currency) {
        return currency == null || currency.isBlank() ? "EUR" : currency.toUpperCase(Locale.ROOT);
    }

    private static double nz(Double v) {
        return v != null ? v : 0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** Ratio → pourcentage à deux décimales. */
    private static double pct(double ratio) {
        return Math.round(ratio * 10000) / 100.0;
    }
}

package com.portfolio.tracker.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Données de référence géographiques : pays (nom Yahoo anglais → code ISO et
 * nom français), devise d'un pays, et répartition approximative des grands
 * indices pour estimer les pays d'un ETF (Yahoo ne la fournit pas).
 */
public final class Geography {

    /** Pays non déterminé / regroupements. */
    public static final String UNKNOWN = "??";
    public static final String OTHER_DEVELOPED = "XD";
    public static final String OTHER_EMERGING = "XE";
    public static final String OTHER = "XX";

    private Geography() {
    }

    public record Country(String code, String name, String currency) {
    }

    private static final Map<String, Country> BY_CODE = new LinkedHashMap<>();
    private static final Map<String, String> CODE_BY_ENGLISH = new LinkedHashMap<>();

    private static void add(String code, String french, String currency, String... english) {
        BY_CODE.put(code, new Country(code, french, currency));
        for (String e : english) {
            CODE_BY_ENGLISH.put(e.toLowerCase(Locale.ROOT), code);
        }
    }

    static {
        add("US", "États-Unis", "USD", "United States", "USA");
        add("FR", "France", "EUR", "France");
        add("DE", "Allemagne", "EUR", "Germany");
        add("NL", "Pays-Bas", "EUR", "Netherlands");
        add("ES", "Espagne", "EUR", "Spain");
        add("IT", "Italie", "EUR", "Italy");
        add("BE", "Belgique", "EUR", "Belgium");
        add("IE", "Irlande", "EUR", "Ireland");
        add("FI", "Finlande", "EUR", "Finland");
        add("AT", "Autriche", "EUR", "Austria");
        add("PT", "Portugal", "EUR", "Portugal");
        add("LU", "Luxembourg", "EUR", "Luxembourg");
        add("GR", "Grèce", "EUR", "Greece");
        add("GB", "Royaume-Uni", "GBP", "United Kingdom", "UK", "Jersey", "Guernsey", "Isle of Man");
        add("CH", "Suisse", "CHF", "Switzerland");
        add("SE", "Suède", "SEK", "Sweden");
        add("DK", "Danemark", "DKK", "Denmark");
        add("NO", "Norvège", "NOK", "Norway");
        add("JP", "Japon", "JPY", "Japan");
        add("CA", "Canada", "CAD", "Canada");
        add("AU", "Australie", "AUD", "Australia");
        add("CN", "Chine", "CNY", "China");
        add("HK", "Hong Kong", "HKD", "Hong Kong");
        add("TW", "Taïwan", "TWD", "Taiwan");
        add("KR", "Corée du Sud", "KRW", "South Korea", "Korea");
        add("IN", "Inde", "INR", "India");
        add("BR", "Brésil", "BRL", "Brazil");
        add("MX", "Mexique", "MXN", "Mexico");
        add("SA", "Arabie saoudite", "SAR", "Saudi Arabia");
        add("ZA", "Afrique du Sud", "ZAR", "South Africa");
        add("SG", "Singapour", "SGD", "Singapore");
        add("IL", "Israël", "ILS", "Israel");
        add("BM", "Bermudes", "USD", "Bermuda");
        add("KY", "Îles Caïmans", "USD", "Cayman Islands");
        add("UY", "Uruguay", "USD", "Uruguay");
        add("AR", "Argentine", "USD", "Argentina");
        BY_CODE.put(OTHER_DEVELOPED, new Country(OTHER_DEVELOPED, "Autres pays développés", null));
        BY_CODE.put(OTHER_EMERGING, new Country(OTHER_EMERGING, "Autres pays émergents", null));
        BY_CODE.put(OTHER, new Country(OTHER, "Autres pays", null));
        BY_CODE.put(UNKNOWN, new Country(UNKNOWN, "Non déterminé", null));
    }

    /** Code ISO d'un pays Yahoo (« United States » → US) ; UNKNOWN si inconnu. */
    public static String code(String yahooCountry) {
        if (yahooCountry == null || yahooCountry.isBlank()) {
            return UNKNOWN;
        }
        return CODE_BY_ENGLISH.getOrDefault(yahooCountry.trim().toLowerCase(Locale.ROOT), OTHER);
    }

    public static Country country(String code) {
        return BY_CODE.getOrDefault(code, BY_CODE.get(OTHER));
    }

    /** Devise d'un pays (null pour les regroupements). */
    public static String currency(String code) {
        return country(code).currency();
    }

    // ------------------------------------------------------------ indices

    /** Répartition approximative (2025) d'un indice, par pays ; somme = 1. */
    private record IndexWeights(List<String> keywords, Map<String, Double> weights) {
    }

    private static Map<String, Double> w(Object... pairs) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue() / 100);
        }
        return m;
    }

    /** Ordre important : du plus précis au plus large (« All-World » avant « World »). */
    private static final List<IndexWeights> INDICES = List.of(
            new IndexWeights(List.of("acwi", "all-world", "all world", "all country", "ftse all"),
                    w("US", 64, "JP", 4.9, "GB", 3.2, "CN", 2.8, "CA", 2.7, "FR", 2.4, "IN", 2, "CH", 2.1, "TW", 2,
                            "DE", 2, OTHER, 11.9)),
            new IndexWeights(List.of("emerging", "émergent", "emergent", " em ", "em imi", "msci em"),
                    w("CN", 27, "IN", 19, "TW", 19, "KR", 9.5, "BR", 4.3, "SA", 3.8, "ZA", 3.2, "MX", 1.9, OTHER_EMERGING, 12.3)),
            new IndexWeights(List.of("euro stoxx", "eurostoxx", "emu", "eurozone", "euro area", "zone euro"),
                    w("FR", 32, "DE", 28, "NL", 15, "ES", 9, "IT", 8, "FI", 3, "BE", 2.5, OTHER_DEVELOPED, 2.5)),
            new IndexWeights(List.of("stoxx europe 600", "stoxx 600", "msci europe", "europe"),
                    w("GB", 22, "FR", 16.5, "CH", 15, "DE", 14, "NL", 7, "SE", 5.5, "ES", 4.5, "IT", 4, "DK", 4, OTHER_DEVELOPED, 7.5)),
            new IndexWeights(List.of("world", "monde", "developed"),
                    w("US", 72, "JP", 5.5, "GB", 3.6, "CA", 3, "FR", 2.7, "CH", 2.4, "DE", 2.3, "AU", 1.7, "NL", 1.2,
                            OTHER_DEVELOPED, 5.6)),
            new IndexWeights(List.of("s&p", "s & p", "nasdaq", "dow jones", "russell", "usa", "u.s.", " us ", "united states",
                    "amerique", "amérique"), w("US", 100)),
            new IndexWeights(List.of("cac", "france"), w("FR", 100)),
            new IndexWeights(List.of("dax", "germany", "allemagne"), w("DE", 100)),
            new IndexWeights(List.of("ftse 100", "united kingdom", " uk "), w("GB", 100)),
            new IndexWeights(List.of("japan", "japon", "topix", "nikkei"), w("JP", 100)),
            new IndexWeights(List.of("china", "chine"), w("CN", 100)),
            new IndexWeights(List.of("india", "inde"), w("IN", 100)));

    /**
     * Pays estimés d'un ETF ou d'un fonds d'après l'indice cité dans son nom ;
     * vide si aucun indice n'est reconnu.
     */
    public static Map<String, Double> estimateFromName(String name) {
        if (name == null) {
            return Map.of();
        }
        String n = " " + normalize(name) + " ";
        for (IndexWeights index : INDICES) {
            for (String k : index.keywords()) {
                if (n.contains(normalize(k))) {
                    return index.weights();
                }
            }
        }
        return Map.of();
    }

    /** Minuscules, tirets remplacés par des espaces : « All-World » = « all world ». */
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace('-', ' ');
    }
}

package com.portfolio.tracker.tax;

import java.util.Locale;
import java.util.Map;

/**
 * Crédit d'impôt sur les dividendes d'actions étrangères (case 2AB), calcul
 * pur : la retenue à la source prévue par la convention fiscale du pays ouvre
 * droit à un crédit égal à cette retenue, dans la limite de l'impôt français
 * (12,8 % avec la flat tax). Estimation : l'IFU fait foi.
 */
public final class ForeignDividendCredit {

    /** Plafond : l'impôt français sur le dividende (part impôt de la flat tax). */
    static final double CAP_PCT = 12.8;

    /** Taux de retenue conventionnel (%) par pays ; absent = pas de crédit estimé. */
    private static final Map<String, Double> TREATY_RATE = Map.ofEntries(
            Map.entry("US", 15.0), Map.entry("CA", 15.0), Map.entry("CH", 15.0), Map.entry("DE", 15.0),
            Map.entry("NL", 15.0), Map.entry("ES", 15.0), Map.entry("IT", 15.0), Map.entry("BE", 15.0),
            Map.entry("AT", 15.0), Map.entry("PT", 15.0), Map.entry("SE", 15.0), Map.entry("DK", 15.0),
            Map.entry("NO", 15.0), Map.entry("AU", 15.0), Map.entry("IE", 15.0), Map.entry("JP", 10.0),
            Map.entry("GB", 0.0), Map.entry("HK", 0.0));

    /** Place de cotation Yahoo → pays (suffixe du symbole ; sans suffixe : marché américain). */
    private static final Map<String, String> SUFFIX_COUNTRY = Map.ofEntries(
            Map.entry("PA", "FR"), Map.entry("DE", "DE"), Map.entry("F", "DE"), Map.entry("DU", "DE"),
            Map.entry("MU", "DE"), Map.entry("SG", "DE"), Map.entry("HM", "DE"), Map.entry("AS", "NL"),
            Map.entry("MC", "ES"), Map.entry("MI", "IT"), Map.entry("BR", "BE"), Map.entry("LS", "PT"),
            Map.entry("IR", "IE"), Map.entry("HE", "FI"), Map.entry("VI", "AT"), Map.entry("L", "GB"),
            Map.entry("SW", "CH"), Map.entry("ST", "SE"), Map.entry("CO", "DK"), Map.entry("OL", "NO"),
            Map.entry("TO", "CA"), Map.entry("V", "CA"), Map.entry("T", "JP"), Map.entry("HK", "HK"),
            Map.entry("AX", "AU"));

    private ForeignDividendCredit() {
    }

    /**
     * Pays de la société : celui de son profil s'il est connu (code ISO), sinon
     * déduit de la place de cotation ; null si indéterminable.
     */
    public static String country(String symbol, String profileCountryCode) {
        if (profileCountryCode != null && profileCountryCode.length() == 2) {
            return profileCountryCode;
        }
        if (symbol == null || symbol.isBlank() || symbol.startsWith("~")) {
            return null;
        }
        int dot = symbol.lastIndexOf('.');
        if (dot < 0) {
            return "US";
        }
        return SUFFIX_COUNTRY.get(symbol.substring(dot + 1).toUpperCase(Locale.ROOT));
    }

    /** Taux du crédit (%) pour un dividende de ce pays : 0 pour la France ou un pays inconnu. */
    public static double ratePct(String countryCode) {
        if (countryCode == null || countryCode.equals("FR")) {
            return 0;
        }
        return Math.min(TREATY_RATE.getOrDefault(countryCode, 0.0), CAP_PCT);
    }
}

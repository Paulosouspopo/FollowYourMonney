package com.portfolio.tracker.imports;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Actif tel que désigné dans un relevé, avant résolution en symbole Yahoo.
 *
 * @param reference         clé stable (sert à mémoriser la correspondance) :
 *                          {@code ISIN:FR0000133308}, {@code CRYPTO:BTC} ou {@code NAME:...}
 * @param label             libellé lisible (nom du relevé)
 * @param isin              ISIN s'il est connu
 * @param cryptoCode        code crypto (BTC, ETH...) s'il s'agit d'une crypto
 * @param preferredExchange place indiquée par le relevé (ex : « Paris »), pour
 *                          départager les résultats de recherche
 */
public record AssetRef(String reference, String label, String isin, String cryptoCode, String preferredExchange) {

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    public static boolean looksLikeIsin(String value) {
        return value != null && ISIN.matcher(value.trim().toUpperCase(Locale.ROOT)).matches();
    }

    public static AssetRef isin(String isin, String label) {
        String code = isin.trim().toUpperCase(Locale.ROOT);
        return new AssetRef("ISIN:" + code, blankTo(label, code), code, null, null);
    }

    public static AssetRef crypto(String code, String label) {
        String c = code.trim().toUpperCase(Locale.ROOT);
        return new AssetRef("CRYPTO:" + c, blankTo(label, c), null, c, null);
    }

    public static AssetRef name(String name, String preferredExchange) {
        String n = name.trim().replaceAll("\\s+", " ");
        return new AssetRef("NAME:" + n.toUpperCase(Locale.ROOT), n, null, null, preferredExchange);
    }

    /** Valeur libre d'une colonne « actif » (import générique) : ISIN si reconnu, sinon recherche par nom. */
    public static AssetRef guess(String value) {
        return looksLikeIsin(value) ? isin(value, null) : name(value, null);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

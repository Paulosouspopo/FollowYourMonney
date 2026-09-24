package com.portfolio.tracker.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Mise en forme française des montants et pourcentages dans les messages. */
public final class Formats {

    private static final Locale FR = Locale.FRANCE;

    private Formats() {
    }

    /** « 12 345,67 € » */
    public static String eur(BigDecimal amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(FR);
        return normalizeSpaces(f.format(amount.setScale(2, RoundingMode.HALF_UP)));
    }

    /** « 184,20 $US » : cours dans la devise de cotation (EUR si inconnue). */
    public static String money(BigDecimal amount, String currency) {
        NumberFormat f = NumberFormat.getCurrencyInstance(FR);
        try {
            f.setCurrency(java.util.Currency.getInstance(currency != null ? currency : "EUR"));
        } catch (IllegalArgumentException e) {
            return eur(amount);
        }
        int digits = amount.abs().compareTo(BigDecimal.ONE) < 0 ? 6 : 2;
        f.setMaximumFractionDigits(digits);
        return normalizeSpaces(f.format(amount.setScale(digits, RoundingMode.HALF_UP)));
    }

    /** « +312,40 € » / « −312,40 € » */
    public static String signedEur(BigDecimal amount) {
        return sign(amount) + eur(amount.abs());
    }

    /** « +3,42 % » / « −3,42 % » (valeur déjà en %) */
    public static String signedPercent(BigDecimal percent) {
        NumberFormat f = NumberFormat.getNumberInstance(FR);
        f.setMinimumFractionDigits(1);
        f.setMaximumFractionDigits(2);
        return sign(percent) + normalizeSpaces(f.format(percent.abs())) + " %";
    }

    /** « 3,5 % » (seuil d'une règle) */
    public static String percent(BigDecimal percent) {
        NumberFormat f = NumberFormat.getNumberInstance(FR);
        f.setMaximumFractionDigits(2);
        return normalizeSpaces(f.format(percent)) + " %";
    }

    private static String sign(BigDecimal v) {
        return v.signum() > 0 ? "+" : v.signum() < 0 ? "−" : "";
    }

    /** Espaces insécables de la locale → espaces simples (lisibles dans un email texte). */
    private static String normalizeSpaces(String s) {
        return s.replace(' ', ' ').replace(' ', ' ');
    }
}

package com.portfolio.tracker.asset;

import java.security.SecureRandom;

/**
 * Actifs non cotés : leur symbole interne commence par « ~ », caractère
 * absent des symboles Yahoo. Il n'est jamais envoyé à Yahoo : les valeurs
 * sont saisies par l'utilisateur et stockées comme des cours (asset_prices),
 * si bien que la valorisation et l'historique les traitent comme les autres.
 */
public final class ManualAssets {

    public static final String PREFIX = "~";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ManualAssets() {
    }

    public static boolean isManual(String symbol) {
        return symbol != null && symbol.startsWith(PREFIX);
    }

    /** Symbole interne unique : « ~ » + 12 caractères (collision négligeable). */
    public static String newSymbol() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

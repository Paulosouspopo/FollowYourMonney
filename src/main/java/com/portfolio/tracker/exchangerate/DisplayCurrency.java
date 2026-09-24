package com.portfolio.tracker.exchangerate;

import com.portfolio.tracker.shared.exception.BadRequestException;

import java.util.List;

/**
 * Devises d'affichage proposées. Les calculs restent en EUR : l'affichage
 * convertit au taux du jour (montants actuels) ou au taux historique du jour
 * concerné (courbe).
 */
public final class DisplayCurrency {

    public static final List<String> SUPPORTED = List.of("EUR", "USD", "GBP", "CHF");

    private DisplayCurrency() {
    }

    /** Devise validée, en majuscules ; null ou vide = EUR. */
    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            return "EUR";
        }
        String code = raw.trim().toUpperCase();
        if (!SUPPORTED.contains(code)) {
            throw new BadRequestException("Devise d'affichage non gérée : " + raw + " (" + String.join(", ", SUPPORTED) + ")");
        }
        return code;
    }
}

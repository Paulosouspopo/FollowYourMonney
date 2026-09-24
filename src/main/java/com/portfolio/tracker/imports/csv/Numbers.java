package com.portfolio.tracker.imports.csv;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Nombres des relevés : « 1 234,56 », « 1.234,56 », « 1234.56 », « -0.44 »,
 * « null », vide.
 */
public final class Numbers {

    private Numbers() {
    }

    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim()
                .replace(" ", "")
                .replace(" ", "")
                .replace(" ", "")
                .replace("€", "");
        if (s.isEmpty() || s.equalsIgnoreCase("null") || s.equals("-")) {
            return Optional.empty();
        }
        if (s.contains(",")) {
            if (s.contains(".")) {
                // Le dernier séparateur est le séparateur décimal
                s = s.lastIndexOf(',') > s.lastIndexOf('.')
                        ? s.replace(".", "").replace(',', '.')   // 1.234,56
                        : s.replace(",", "");                     // 1,234.56
            } else {
                s = s.replace(',', '.');                          // 12,5
            }
        }
        try {
            return Optional.of(new BigDecimal(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Valeur absolue, 0 si absente. */
    public static BigDecimal abs(String raw) {
        return parse(raw).map(BigDecimal::abs).orElse(BigDecimal.ZERO);
    }
}

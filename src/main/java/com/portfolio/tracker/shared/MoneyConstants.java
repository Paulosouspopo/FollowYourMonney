package com.portfolio.tracker.shared;

import java.math.RoundingMode;

/**
 * Constantes monétaires de l'application.
 *
 * RÈGLE D'OR : tout montant persisté en base l'est en EUR (devise pivot).
 * La devise d'affichage est une préoccupation de la couche présentation,
 * appliquée au dernier moment, jamais stockée.
 */
public final class MoneyConstants {

    /** Devise pivot. Tous les montants en base sont exprimés dans cette devise. */
    public static final String BASE_CURRENCY = "EUR";

    /** Échelle des montants monétaires. */
    public static final int MONEY_SCALE = 2;

    /** Échelle des quantités d'actifs (crypto = nombreuses décimales). */
    public static final int QUANTITY_SCALE = 8;

    /** Échelle des taux de change. */
    public static final int RATE_SCALE = 8;

    /** Échelle des pourcentages. */
    public static final int PERCENT_SCALE = 4;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyConstants() {
        throw new UnsupportedOperationException("Classe utilitaire");
    }
}
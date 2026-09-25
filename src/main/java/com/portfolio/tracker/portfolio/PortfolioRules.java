package com.portfolio.tracker.portfolio;

/** Règles liées au type de portefeuille. */
public final class PortfolioRules {

    private PortfolioRules() {
    }

    /** Un livret ne contient que des liquidités : il ne peut pas détenir d'actifs cotés. */
    public static boolean holdsOnlyCash(PortfolioType type) {
        return type == PortfolioType.LIVRET;
    }

    /**
     * Suivi des liquidités effectif : forcé pour un livret, sinon le choix de
     * l'utilisateur (désactivé par défaut).
     */
    public static boolean cashTracking(PortfolioType type, Boolean requested) {
        return holdsOnlyCash(type) || Boolean.TRUE.equals(requested);
    }
}

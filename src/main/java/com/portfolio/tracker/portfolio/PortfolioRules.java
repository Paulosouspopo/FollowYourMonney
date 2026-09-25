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
     * Enveloppe à versements : assurance-vie, PER, épargne salariale. L'argent
     * y est versé puis investi ; la partie non investie (fonds euros, sommes en
     * attente) est le solde de liquidités du portefeuille.
     */
    public static boolean isSavingsWrapper(PortfolioType type) {
        return type == PortfolioType.ASSURANCE_VIE || type == PortfolioType.PER
                || type == PortfolioType.EPARGNE_SALARIALE;
    }

    /**
     * Enveloppe à fiscalité propre : ses ventes et dividendes internes ne sont
     * pas imposés comme ceux d'un compte-titres (ni déclarés en 2042).
     */
    public static boolean isTaxSheltered(PortfolioType type) {
        return type == PortfolioType.PEA || holdsOnlyCash(type) || isSavingsWrapper(type);
    }

    /** L'employeur peut y verser un abondement. */
    public static boolean acceptsEmployerContribution(PortfolioType type) {
        return type == PortfolioType.EPARGNE_SALARIALE || type == PortfolioType.PER;
    }

    /**
     * Suivi des liquidités effectif : forcé pour un livret et une enveloppe à
     * versements, sinon le choix de l'utilisateur (désactivé par défaut).
     */
    public static boolean cashTracking(PortfolioType type, Boolean requested) {
        return holdsOnlyCash(type) || isSavingsWrapper(type) || Boolean.TRUE.equals(requested);
    }
}

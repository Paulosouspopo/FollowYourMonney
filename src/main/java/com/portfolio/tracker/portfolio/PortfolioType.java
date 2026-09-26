package com.portfolio.tracker.portfolio;

public enum PortfolioType {
    PEA, CTO, CRYPTO, LIVRET,
    /** Assurance-vie : fonds euros (liquidités du contrat) + unités de compte. */
    ASSURANCE_VIE,
    /** Plan d'épargne retraite : même structure qu'une assurance-vie, versements déductibles. */
    PER,
    /** PEE, PERCO, PERECO : fonds d'entreprise, versements, intéressement et abondement. */
    EPARGNE_SALARIALE,
    IMMOBILIER, AUTRE
}

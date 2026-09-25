package com.portfolio.tracker.quality;

import com.portfolio.tracker.asset.AssetType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

/**
 * Règles de cohérence d'une saisie, sans base ni réseau (testées à part).
 * Elles ne bloquent rien : elles signalent ce qui ressemble à une erreur.
 */
public final class QualityRules {

    /**
     * Écart toléré entre le prix saisi et la clôture du jour : large, car la
     * clôture n'est ni le plus haut ni le plus bas de la séance.
     */
    static final BigDecimal TOLERANCE = new BigDecimal("0.15");
    static final BigDecimal CRYPTO_TOLERANCE = new BigDecimal("0.25");

    /** Places de cotation de l'Union européenne (suffixes Yahoo) : actions éligibles au PEA possibles. */
    private static final Set<String> EU_SUFFIXES = Set.of(
            "PA", "AS", "BR", "LS", "IR", "DE", "F", "MU", "SG", "HM", "DU", "BE", "MI", "MC", "VI", "HE", "ST",
            "CO", "AT", "WA", "PR", "BD", "RG", "TL", "VS", "LU", "NL");

    private QualityRules() {
    }

    /** Écart relatif (0.75 = +75 %), null si le cours de marché est inexploitable. */
    public static BigDecimal deviation(BigDecimal price, BigDecimal market) {
        if (price == null || market == null || market.signum() <= 0) {
            return null;
        }
        return price.subtract(market).divide(market, 4, RoundingMode.HALF_UP);
    }

    /**
     * Rapport prix saisi / cours de marché à partir duquel on soupçonne une
     * division (ou un regroupement) d'actions plutôt qu'une faute de frappe :
     * les séries de marché sont corrigées après une division, pas les relevés.
     */
    static final BigDecimal SPLIT_RATIO = new BigDecimal("5");

    /**
     * Facteur de division probable (arrondi, ex. 200 ; ou 0,1 pour un regroupement
     * par 10), si l'écart est trop grand pour une erreur de saisie. Sinon vide.
     */
    public static Optional<BigDecimal> splitFactor(BigDecimal price, BigDecimal market) {
        if (price == null || market == null || price.signum() <= 0 || market.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal ratio = price.divide(market, 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(SPLIT_RATIO) >= 0) {
            return Optional.of(ratio.setScale(0, RoundingMode.HALF_UP));
        }
        BigDecimal inverse = market.divide(price, 6, RoundingMode.HALF_UP);
        if (inverse.compareTo(SPLIT_RATIO) >= 0) {
            return Optional.of(BigDecimal.ONE.divide(inverse.setScale(0, RoundingMode.HALF_UP), 6, RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    public static boolean isSuspicious(BigDecimal deviation, AssetType type) {
        if (deviation == null) {
            return false;
        }
        BigDecimal tolerance = type == AssetType.CRYPTO ? CRYPTO_TOLERANCE : TOLERANCE;
        return deviation.abs().compareTo(tolerance) > 0;
    }

    /** Raison pour laquelle l'actif n'a probablement pas sa place dans un PEA, sinon vide. */
    public static Optional<String> peaProblem(String symbol, AssetType type) {
        if (type == AssetType.CRYPTO) {
            return Optional.of("Les cryptomonnaies ne sont pas éligibles au PEA.");
        }
        if (type != AssetType.ACTION && type != AssetType.ETF) {
            return Optional.empty();
        }
        int dot = symbol.lastIndexOf('.');
        String suffix = dot < 0 ? "" : symbol.substring(dot + 1).toUpperCase();
        if (!EU_SUFFIXES.contains(suffix)) {
            return Optional.of("Cotation hors Union européenne : probablement non éligible au PEA.");
        }
        return Optional.empty();
    }
}

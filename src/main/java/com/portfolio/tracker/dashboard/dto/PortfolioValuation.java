package com.portfolio.tracker.dashboard.dto;

import com.portfolio.tracker.portfolio.PortfolioType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Valorisation d'un portefeuille. Montants en EUR.
 *
 * Investi = apports nets (argent sorti de la poche) : valeur - investi = gain
 * total (latent + réalisé + dividendes + intérêts - frais). La plus-value
 * latente reste celle des positions, en % de leur seul prix de revient.
 */
@Getter
@Builder
public class PortfolioValuation {

    private UUID portfolioId;
    private String name;
    private PortfolioType type;

    /** Positions + solde positif des liquidités (si suivies). */
    private BigDecimal currentValueEur;
    /**
     * Apports nets : versements - retraits + découvert (compte suivi), achats -
     * ventes - dividendes (sans suivi). Peut être négatif sans suivi.
     */
    private BigDecimal investedEur;
    /** Prix de revient des positions détenues. */
    private BigDecimal positionsCostEur;
    private BigDecimal unrealizedGainEur;
    /** Plus-value latente / prix de revient des positions (hors liquidités). */
    private BigDecimal unrealizedGainPercentage;
    private BigDecimal realizedGainEur;
    private BigDecimal dividendsEur;
    /** Intérêts crédités (livret, rémunération des espèces). */
    private BigDecimal interestEur;
    /** Frais de courtage + frais de tenue de compte. */
    private BigDecimal totalFeesEur;

    private boolean cashTracking;
    /** Solde de liquidités (0 si non suivi). Peut être négatif. */
    private BigDecimal cashEur;
    /** Versements - retraits (+ abondement) : argent apporté de l'extérieur. */
    private BigDecimal netDepositsEur;
    /** Dont abondement de l'employeur (épargne salariale, PER). */
    private BigDecimal employerContributionsEur;
    /** Compte multidevise : opérations réglées dans leur devise. */
    private boolean multiCurrencyCash;
    /** Soldes par devise, vide si le compte n'a que des euros. */
    private List<CashBalance> cashBalances;
    /** Taux affiché d'un livret, en %. */
    private BigDecimal annualInterestRate;

    private List<PositionValuation> positions;

    /** Nombre de positions encore ouvertes. */
    private int openPositionCount;

    /** true si au moins un cours de marché manquait (position estimée). */
    private boolean hasIncompletePrices;
}

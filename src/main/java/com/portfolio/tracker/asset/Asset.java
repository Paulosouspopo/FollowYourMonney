package com.portfolio.tracker.asset;

import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.transaction.Transaction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    /**
     * Symbole utilisé pour interroger Yahoo Finance.
     * Ex: "AAPL", "BTC-USD", "CW8.PA", "EURUSD=X"
     */
    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    private String currency;

    /**
     * Nom complet renvoyé par Yahoo (meta.longName), utile pour l'UI.
     * Peut être null tant que le premier fetch n'a pas eu lieu.
     */
    private String longName;

    /**
     * Place boursière renvoyée par Yahoo (meta.fullExchangeName).
     * Ex: "NasdaqGS", "CCC" (crypto), "Paris"
     */
    private String exchangeName;

    /**
     * Actif non coté (fonds absent de Yahoo, FCPE…) : valeurs saisies par
     * l'utilisateur, symbole interne (voir {@link ManualAssets}).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean manual = false;

    /** Frais courants annuels (TER, en %) renseignés par l'utilisateur, prioritaires sur ceux de Yahoo. */
    @Column(name = "annual_fee_pct", precision = 6, scale = 3)
    private java.math.BigDecimal annualFeePct;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL)
    private List<Transaction> transactions;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
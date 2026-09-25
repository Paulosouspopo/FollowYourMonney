package com.portfolio.tracker.portfolio;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortfolioType type;

    private String description;

    /**
     * Le solde de liquidités (versements, retraits, intérêts, flux des
     * opérations) entre dans la valeur. Toujours vrai pour un livret.
     */
    @Column(name = "cash_tracking", nullable = false)
    @Builder.Default
    private boolean cashTracking = false;

    /** Date d'ouverture du compte (PEA : point de départ des 5 ans). Null = première opération. */
    @Column(name = "opened_at")
    private java.time.LocalDate openedAt;

    /** Taux annuel affiché d'un livret, en % (informatif). */
    @Column(name = "annual_interest_rate", precision = 6, scale = 3)
    private BigDecimal annualInterestRate;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Asset> assets = new java.util.ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

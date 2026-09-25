package com.portfolio.tracker.cash;

import com.portfolio.tracker.portfolio.Portfolio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

/**
 * Mouvement d'argent sur un portefeuille, en EUR ou dans une devise du compte.
 * Le montant est toujours positif : {@link #getType()} donne le sens du flux.
 */
@Entity
@Table(name = "cash_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashMovement {

    /** Ordre de rejeu : date, puis ordre de saisie. */
    public static final Comparator<CashMovement> CHRONOLOGICAL = Comparator
            .comparing(CashMovement::getMovementDate)
            .thenComparing(CashMovement::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashMovementType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Devise du montant (EUR sauf compte multidevise). */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    /** Taux vers l'euro du jour du mouvement (1 en EUR), figé comme sur une transaction. */
    @Column(name = "exchange_rate_to_eur", nullable = false, precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal exchangeRateToEur = BigDecimal.ONE;

    /** Change (CONVERSION) : montant reçu, dans {@link #counterCurrency}. */
    @Column(name = "counter_amount", precision = 19, scale = 2)
    private BigDecimal counterAmount;

    @Column(name = "counter_currency", length = 3)
    private String counterCurrency;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(length = 500)
    private String notes;

    /** Référence dans le relevé importé (anti-doublon), null pour une saisie manuelle. */
    @Column(name = "external_ref", length = 100)
    private String externalRef;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Effet sur le solde de sa devise (positif = entrée d'argent). */
    public BigDecimal signedAmount() {
        return type.signed(amount);
    }

    /** Montant en euros au taux du jour du mouvement. */
    public BigDecimal amountEur() {
        return amount.multiply(exchangeRateToEur).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

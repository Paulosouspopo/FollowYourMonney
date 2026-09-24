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
 * Mouvement d'argent sur un portefeuille, en EUR.
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

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Effet sur le solde (positif = entrée d'argent). */
    public BigDecimal signedAmount() {
        return type.signed(amount);
    }
}

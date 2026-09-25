package com.portfolio.tracker.plan;

import com.portfolio.tracker.portfolio.Portfolio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Investissement programmé : achat d'un actif (BUY) ou versement d'espèces
 * (DEPOSIT) à intervalle régulier. Chaque échéance échue devient une vraie
 * transaction / un vrai mouvement, au cours de clôture du jour (prix estimé),
 * avec la référence {@code PLAN:<id>:<date>} qui la rend idempotente.
 */
@Entity
@Table(name = "investment_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentPlan {

    public enum Type { BUY, DEPOSIT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(length = 64)
    private String symbol;

    @Column(nullable = false)
    private String name;

    /** Montant de chaque échéance en EUR, frais compris. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** false : parts entières uniquement, le reliquat n'est pas investi. */
    @Column(nullable = false)
    @Builder.Default
    private boolean fractional = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Échéances déjà traitées (exécutées ou sautées) depuis la date de début. */
    @Column(nullable = false)
    @Builder.Default
    private int occurrences = 0;

    /** null : plan terminé (date de fin dépassée). */
    @Column(name = "next_execution_date")
    private LocalDate nextExecutionDate;

    @Column(name = "last_execution_date")
    private LocalDate lastExecutionDate;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Date de l'échéance suivante, null si elle dépasse la date de fin. */
    public LocalDate computeNextDate() {
        LocalDate next = frequency.occurrence(startDate, occurrences);
        return endDate != null && next.isAfter(endDate) ? null : next;
    }

    /** Passe à l'échéance suivante (exécutée ou sautée). */
    public void advance() {
        occurrences++;
        nextExecutionDate = computeNextDate();
    }

    public String executionRef(LocalDate day) {
        return "PLAN:" + id + ":" + day;
    }
}

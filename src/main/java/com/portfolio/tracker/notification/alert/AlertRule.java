package com.portfolio.tracker.notification.alert;

import com.portfolio.tracker.portfolio.Portfolio;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Règle d'alerte : « quand {@link #scope} {@link #condition} {@link #threshold}
 * sur {@link #period} → prévenir ». Voir {@link AlertEvaluator}.
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    /** Patrimoine total, un portefeuille, ou un actif (détenu ou non : « surveille le BTC »). */
    public enum Scope { GLOBAL, PORTFOLIO, ASSET }

    /**
     * RISES / FALLS / MOVES : variation en % sur la période ; ABOVE / BELOW :
     * valeur (patrimoine, portefeuille) ou cours (actif) en EUR.
     */
    public enum Condition {
        RISES, FALLS, MOVES, ABOVE, BELOW;

        public boolean isPercentage() {
            return this == RISES || this == FALLS || this == MOVES;
        }
    }

    /** Période d'une variation : depuis la clôture de la veille, 7 jours, 30 jours. */
    public enum Period {
        DAY(1), WEEK(7), MONTH(30);

        private final int days;

        Period(int days) {
            this.days = days;
        }

        public int days() {
            return days;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @Column(length = 64)
    private String symbol;

    @Column(name = "asset_name")
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private Condition condition;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Period period;

    @Column(name = "notify_email", nullable = false)
    @Builder.Default
    private boolean notifyEmail = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** false après un déclenchement : pas de rappel tant que la condition reste vraie. */
    @Column(nullable = false)
    @Builder.Default
    private boolean armed = true;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

package com.portfolio.tracker.notification;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Préférences de notification d'un utilisateur. Heures calmes : pas de push
 * (la notification reste dans l'app). Plage pouvant passer minuit (22 h → 7 h).
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "quiet_start")
    private Integer quietStart;

    @Column(name = "quiet_end")
    private Integer quietEnd;

    public static NotificationPreferences defaults(UUID userId) {
        return NotificationPreferences.builder().userId(userId).build();
    }

    /** true si {@code time} tombe dans les heures calmes. */
    public boolean isQuiet(LocalTime time) {
        if (quietStart == null || quietEnd == null || quietStart.equals(quietEnd)) {
            return false;
        }
        int hour = time.getHour();
        return quietStart < quietEnd
                ? hour >= quietStart && hour < quietEnd
                : hour >= quietStart || hour < quietEnd;
    }
}

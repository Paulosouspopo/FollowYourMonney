package com.portfolio.tracker.notification.report;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/** Rapport périodique d'un utilisateur (valeurs par défaut : désactivé, 19 h, email). */
@Entity
@Table(name = "report_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSettings {

    /** WEEKLY : le lundi. */
    public enum Frequency { NONE, DAILY, WEEKLY }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Frequency frequency = Frequency.NONE;

    /** Heure d'envoi, fuseau de Paris. */
    @Column(name = "send_hour", nullable = false)
    @Builder.Default
    private int sendHour = 19;

    @Column(name = "notify_email", nullable = false)
    @Builder.Default
    private boolean notifyEmail = true;

    @Column(name = "last_sent_date")
    private LocalDate lastSentDate;

    public static ReportSettings defaults(UUID userId) {
        return ReportSettings.builder().userId(userId).build();
    }
}

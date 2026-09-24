package com.portfolio.tracker.notification.push;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Abonnement Web Push d'un navigateur (un par appareil). */
@Entity
@Table(name = "push_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** URL du service de push (Google, Mozilla, Apple) propre à ce navigateur. */
    @Column(nullable = false, length = 1000)
    private String endpoint;

    /** Clé publique du navigateur (chiffrement du message). */
    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    @Column(name = "device_label", length = 200)
    private String deviceLabel;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;
}

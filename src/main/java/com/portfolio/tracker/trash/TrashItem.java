package com.portfolio.tracker.trash;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Élément supprimé, restaurable 30 jours : instantané JSON de ce qu'il faut recréer. */
@Entity
@Table(name = "trash_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrashItem {

    public enum Kind { TRANSACTION, CASH_MOVEMENT, PORTFOLIO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    /** Portefeuille d'origine (opération, mouvement) ou supprimé (portefeuille). */
    @Column(name = "portfolio_id")
    private UUID portfolioId;

    /** Ce que l'utilisateur voit dans la corbeille : « Achat · Air Liquide · 12/03/2025 ». */
    @Column(nullable = false)
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;
}

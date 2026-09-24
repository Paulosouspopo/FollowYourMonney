package com.portfolio.tracker.assetprice;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Intervalle de jours déjà demandé au provider pour un symbole.
 *
 * On mémorise ce qu'on a DEMANDÉ, pas ce qu'on a reçu : les week-ends, jours
 * fériés ou dates antérieures à l'introduction en bourse n'ont pas de prix,
 * et ne doivent pas être redemandés à chaque fois. L'intervalle est toujours
 * contigu, donc aucun trou ne peut apparaître au milieu de l'historique.
 *
 * {@code coveredTo < coveredFrom} signifie "aucun jour clos couvert".
 */
@Entity
@Table(name = "price_history_coverage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistoryCoverage {

    @Id
    @Column(length = 64)
    private String symbol;

    @Column(nullable = false)
    private LocalDate coveredFrom;

    /** Dernier jour CLOS couvert (jamais aujourd'hui : la journée n'est pas finie). */
    @Column(nullable = false)
    private LocalDate coveredTo;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

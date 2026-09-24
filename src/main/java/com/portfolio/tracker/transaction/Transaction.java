package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

/**
 * Opération sur un actif.
 *
 * Modèle monétaire :
 *  - {@code totalAmount} / {@code fees} / {@code pricePerUnit} sont dans la
 *    devise d'origine de l'opération ({@code currency}) : ce que l'utilisateur
 *    a réellement saisi, jamais recalculé.
 *  - {@code totalAmountEur} / {@code feesEur} sont figés en EUR au taux du jour
 *    de l'opération ({@code exchangeRateToEur}) : c'est la vérité historique
 *    qui sert à tous les calculs de performance.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_asset_date", columnList = "asset_id, transactionDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    /** Ordre de rejeu : date d'opération, puis ordre de saisie (départage deux opérations à la même heure). */
    public static final Comparator<Transaction> CHRONOLOGICAL = Comparator
            .comparing(Transaction::getTransactionDate)
            .thenComparing(Transaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    /** Pour DIVIDEND : nombre de titres ayant généré le dividende, ou 1 si pricePerUnit est le montant total. */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    /** Prix unitaire dans la devise d'origine. */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal pricePerUnit;

    /** Frais de courtage, dans la devise d'origine. */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;

    /** quantity * pricePerUnit (hors frais), devise d'origine. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** Devise d'origine de l'opération (ISO 4217). */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Taux currency -> EUR figé au moment de l'opération. 1 si currency = EUR. */
    @Column(name = "exchange_rate_to_eur", nullable = false, precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal exchangeRateToEur = BigDecimal.ONE;

    /** totalAmount converti en EUR au taux d'époque. Hors frais. */
    @Column(name = "total_amount_eur", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmountEur;

    /** fees converti en EUR au taux d'époque. */
    @Column(name = "fees_eur", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal feesEur = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
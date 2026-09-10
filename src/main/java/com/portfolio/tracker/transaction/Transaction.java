package com.portfolio.tracker.transaction;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.portfolio.tracker.asset.Asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_asset_date", columnList = "asset_id, transactionDate")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /** Pour DIVIDEND : quantité de titres ayant généré le dividende (ou 0). */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    /** Devise d'origine de l'opération. */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal pricePerUnit;

    /** Frais de courtage, dans la devise d'origine. */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;

    /** quantity * pricePerUnit (hors frais), devise d'origine. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** Devise de l'opération. Ex: "USD" */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Taux vers la devise de référence de l'user au moment de l'opération. 1 si identique. */
    @Column(nullable = false, precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** Devise de référence cible (celle de l'user à la saisie). Ex: "EUR" */
    @Column(nullable = false, length = 3)
    private String baseCurrency;

    /** (totalAmount + fees) * exchangeRate — figé, jamais recalculé. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmountInBaseCurrency;

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

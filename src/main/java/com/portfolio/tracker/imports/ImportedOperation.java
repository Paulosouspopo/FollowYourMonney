package com.portfolio.tracker.imports;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Opération lue dans un relevé, dans le vocabulaire de l'application.
 *
 * Opération sur actif : {@code asset}, {@code quantity}, {@code unitPrice},
 * {@code fees}, {@code currency}. Mouvement d'argent : {@code amount} (EUR).
 * Montants toujours positifs : le type donne le sens.
 */
@Getter
@Setter
@Builder
public class ImportedOperation {

    /** Lignes du fichier d'origine (plusieurs pour une opération Binance à deux jambes). */
    private List<Integer> lines;
    private LocalDateTime dateTime;
    private ImportKind kind;

    private AssetRef asset;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;
    private String currency;

    /** Montant d'un mouvement d'argent, en EUR. */
    private BigDecimal amount;

    private String notes;

    /** Identifiant dans le relevé (ou empreinte de la ligne) : anti-doublon. */
    private String externalRef;

    @Builder.Default
    private RowStatus status = RowStatus.READY;
    /** Raison d'un statut IGNORED / ERROR / DUPLICATE, ou avertissement. */
    private String message;

    /**
     * Conversion crypto → crypto : le relevé ne donne pas de valeur en euros.
     * Le prix est estimé au cours de clôture du jour de {@code valuationAsset}
     * pour {@code valuationQuantity} unités.
     */
    private boolean priceEstimated;
    private AssetRef valuationAsset;
    private BigDecimal valuationQuantity;

    public static ImportedOperation ignored(List<Integer> lines, LocalDateTime dateTime, String reason) {
        return ImportedOperation.builder().lines(lines).dateTime(dateTime).status(RowStatus.IGNORED).message(reason).build();
    }

    /** Ligne illisible (date, actif manquant) : à corriger dans le fichier, pas volontairement écartée. */
    public static ImportedOperation error(List<Integer> lines, LocalDateTime dateTime, String reason) {
        return ImportedOperation.builder().lines(lines).dateTime(dateTime).status(RowStatus.ERROR).message(reason).build();
    }
}

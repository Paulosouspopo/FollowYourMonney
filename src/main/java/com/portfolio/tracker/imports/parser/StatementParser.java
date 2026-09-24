package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.imports.ImportFormat;
import com.portfolio.tracker.imports.ImportedOperation;
import com.portfolio.tracker.imports.csv.CsvTable;

import java.util.List;

/**
 * Traduit le relevé d'un courtier en opérations de l'application.
 * Un parser ne fait aucun appel réseau ni accès base : la résolution des
 * actifs, l'estimation des prix et la détection des doublons viennent après
 * ({@code ImportService}).
 */
public interface StatementParser {

    ImportFormat format();

    /** Reconnaît le relevé à ses en-têtes. */
    boolean supports(CsvTable table);

    /** Opérations dans l'ordre chronologique ; les lignes non importables sont IGNORED avec leur raison. */
    List<ImportedOperation> parse(CsvTable table);
}

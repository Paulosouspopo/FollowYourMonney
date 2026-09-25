package com.portfolio.tracker.imports.dto;

import com.portfolio.tracker.imports.ImportFormat;

import java.util.List;
import java.util.Map;

/**
 * Premier coup d'œil sur un fichier : format reconnu (ou null) et extrait,
 * pour l'association manuelle des colonnes d'un relevé inconnu.
 */
public record ImportInspection(
        ImportFormat detectedFormat,
        String encoding,
        String delimiter,
        List<String> headers,
        List<List<String>> sampleRows,
        int rowCount,
        /** Valeurs distinctes des colonnes à faible variété (≤ 20), ex : les types d'opération. */
        Map<String, List<String>> columnValues
) {}

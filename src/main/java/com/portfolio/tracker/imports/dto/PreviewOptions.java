package com.portfolio.tracker.imports.dto;

import com.portfolio.tracker.imports.ImportFormat;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Options de l'aperçu (partie JSON de la requête multipart).
 *
 * @param format  null = détection automatique
 * @param mapping obligatoire pour le format GENERIC
 */
public record PreviewOptions(
        @NotNull(message = "Le portefeuille est obligatoire")
        UUID portfolioId,
        ImportFormat format,
        GenericMapping mapping
) {}

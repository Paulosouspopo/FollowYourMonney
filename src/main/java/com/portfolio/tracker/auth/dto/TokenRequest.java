package com.portfolio.tracker.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Jeton reçu par email (lien de vérification). */
public record TokenRequest(
        @NotBlank(message = "Le lien est invalide")
        String token
) {}

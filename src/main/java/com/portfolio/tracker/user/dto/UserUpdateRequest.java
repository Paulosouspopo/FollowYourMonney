package com.portfolio.tracker.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
        String username,

        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit être un code ISO 4217 (ex: EUR)")
        String preferredCurrency
) {}

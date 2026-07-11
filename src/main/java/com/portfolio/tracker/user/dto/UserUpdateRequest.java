package com.portfolio.tracker.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(

        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        String username,

        String preferredCurrency
) {}
package com.portfolio.tracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        String email
) {}

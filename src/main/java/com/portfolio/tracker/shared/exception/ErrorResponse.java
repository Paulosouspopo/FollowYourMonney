package com.portfolio.tracker.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // n'affiche pas les champs null dans le JSON
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;       // "NOT_FOUND", "VALIDATION_ERROR", etc.
    private String code;        // Code métier stable pour le front (ex : EMAIL_NOT_VERIFIED)
    private String message;     // Message lisible
    private String path;        // /api/assets/123
    private List<FieldError> fieldErrors; // Uniquement pour les erreurs de validation

    @Getter
    @Builder
    public static class FieldError {
        private String field;   // "symbol"
        private String message; // "Le symbole est obligatoire"
    }
}

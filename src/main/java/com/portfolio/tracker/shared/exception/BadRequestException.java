package com.portfolio.tracker.shared.exception;

/**
 * Requête invalide du point de vue métier (paramètre non reconnu,
 * règle de gestion violée). Traduite en HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
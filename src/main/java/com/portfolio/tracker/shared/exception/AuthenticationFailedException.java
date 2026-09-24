package com.portfolio.tracker.shared.exception;

/**
 * Session absente, expirée ou révoquée (jeton de renouvellement invalide).
 * Traduite en HTTP 401 : le client doit se reconnecter.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}

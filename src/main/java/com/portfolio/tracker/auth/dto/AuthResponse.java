package com.portfolio.tracker.auth.dto;

/**
 * Jeton d'accès (JWT court) renvoyé dans le corps. Le jeton de renouvellement,
 * lui, voyage uniquement dans un cookie HttpOnly, illisible par le JavaScript.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        /** Double authentification active : pas de session, ce jeton (5 min) accompagne le code. */
        String twoFactorToken
) {
    public AuthResponse(String accessToken, long expiresIn) {
        this(accessToken, expiresIn, null);
    }
}

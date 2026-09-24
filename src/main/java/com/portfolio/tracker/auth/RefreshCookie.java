package com.portfolio.tracker.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie portant le jeton de renouvellement.
 * <ul>
 * <li>HttpOnly : inaccessible au JavaScript (une faille XSS ne peut pas le voler) ;</li>
 * <li>SameSite=Strict : jamais envoyé depuis un autre site (protège du CSRF) ;</li>
 * <li>Path=/api/auth : envoyé uniquement aux endpoints d'authentification ;</li>
 * <li>Secure : HTTPS uniquement, activé en production ({@code app.auth.cookie-secure}).</li>
 * </ul>
 */
@Component
public class RefreshCookie {

    public static final String NAME = "fym_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;

    public RefreshCookie(@Value("${app.auth.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String rawToken, Duration maxAge) {
        return base(rawToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}

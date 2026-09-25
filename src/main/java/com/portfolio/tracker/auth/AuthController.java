package com.portfolio.tracker.auth;

import com.portfolio.tracker.auth.dto.AuthResponse;
import com.portfolio.tracker.auth.dto.ChangePasswordRequest;
import com.portfolio.tracker.auth.dto.EmailRequest;
import com.portfolio.tracker.auth.dto.LoginRequest;
import com.portfolio.tracker.auth.dto.ResetPasswordRequest;
import com.portfolio.tracker.auth.dto.TokenRequest;
import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.shared.ratelimit.RateLimiter;
import com.portfolio.tracker.user.dto.UserCreateRequest;
import com.portfolio.tracker.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Inscription, connexion et sessions. Les endpoints publics sensibles sont
 * limités en débit par adresse IP (et par email quand ils envoient un email).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Duration QUARTER_HOUR = Duration.ofMinutes(15);
    private static final Duration HOUR = Duration.ofHours(1);

    private final AuthService authService;
    private final RefreshCookie refreshCookie;
    private final RateLimiter rateLimiter;
    private final com.portfolio.tracker.demo.DemoService demoService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserCreateRequest request,
            HttpServletRequest http) {
        rateLimiter.check("register:" + http.getRemoteAddr(), 5, HOUR);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login-ip:" + http.getRemoteAddr(), 20, QUARTER_HOUR);
        rateLimiter.check("login-email:" + request.email().toLowerCase(), 10, QUARTER_HOUR);
        return withSession(authService.login(request.email(), request.password()));
    }

    /** Mode démo : compte invité rempli d'un patrimoine fictif, connecté aussitôt (supprimé après 24 h). */
    @PostMapping("/demo")
    public ResponseEntity<AuthResponse> demo(HttpServletRequest http) {
        rateLimiter.check("demo:" + http.getRemoteAddr(), 5, HOUR);
        return withSession(demoService.start());
    }

    /** Nouveau jeton d'accès à partir du cookie ; appelé aussi au chargement de l'app. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String token,
            HttpServletRequest http) {
        rateLimiter.check("refresh:" + http.getRemoteAddr(), 120, QUARTER_HOUR);
        return withSession(authService.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        authService.logout(token);
        return clearedCookie();
    }

    /** Authentifié : révoque les sessions de tous les appareils. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal CustomUserDetails user) {
        authService.logoutEverywhere(user.getId());
        return clearedCookie();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        rateLimiter.check("verify:" + http.getRemoteAddr(), 20, HOUR);
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    /** Réponse identique que le compte existe ou non (pas d'énumération des comptes). */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.check("mail-ip:" + http.getRemoteAddr(), 10, HOUR);
        rateLimiter.check("mail-email:" + request.email().toLowerCase(), 3, HOUR);
        authService.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    /** Réponse identique que le compte existe ou non (pas d'énumération des comptes). */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimiter.check("mail-ip:" + http.getRemoteAddr(), 10, HOUR);
        rateLimiter.check("mail-email:" + request.email().toLowerCase(), 3, HOUR);
        authService.forgotPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest http) {
        rateLimiter.check("reset:" + http.getRemoteAddr(), 10, HOUR);
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Authentifié : les autres appareils sont déconnectés, l'appareil courant reçoit une nouvelle session. */
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("change-password:" + user.getId(), 10, HOUR);
        return withSession(authService.changePassword(user.getId(), request.currentPassword(), request.newPassword()));
    }

    // ------------------------------------------------------------------ utils

    private ResponseEntity<AuthResponse> withSession(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie.create(session.refreshToken(), session.refreshValidity()).toString())
                .body(new AuthResponse(session.accessToken(), session.expiresInSeconds()));
    }

    private ResponseEntity<Void> clearedCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }
}

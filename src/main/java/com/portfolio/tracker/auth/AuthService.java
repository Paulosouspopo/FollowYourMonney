package com.portfolio.tracker.auth;

import com.portfolio.tracker.security.JwtService;
import com.portfolio.tracker.shared.exception.AuthenticationFailedException;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.EmailNotVerifiedException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.shared.security.SecureTokens;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import com.portfolio.tracker.user.UserService;
import com.portfolio.tracker.user.dto.UserCreateRequest;
import com.portfolio.tracker.user.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cycle de vie des comptes et des sessions.
 *
 * Session = jeton d'accès JWT court (quelques minutes, envoyé à chaque requête)
 * + jeton de renouvellement long (cookie HttpOnly, sert uniquement à obtenir
 * un nouveau JWT). Le jeton de renouvellement tourne à chaque usage ; présenter
 * un jeton déjà utilisé signale un vol probable : toutes les sessions de
 * l'utilisateur sont alors révoquées.
 */
@Service
@Slf4j
public class AuthService {

    /**
     * Deux onglets qui renouvellent en même temps présentent le même jeton :
     * le second arrive juste après la rotation. Dans ce délai, ce n'est pas
     * traité comme un vol (pas de révocation générale).
     */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(20);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEmails accountEmails;
    private final Duration refreshValidity;
    private final com.portfolio.tracker.auth.twofactor.TwoFactorService twoFactorService;

    public AuthService(AuthenticationManager authenticationManager,
            UserRepository userRepository,
            UserService userService,
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            AccountTokenRepository accountTokenRepository,
            PasswordEncoder passwordEncoder,
            AccountEmails accountEmails,
            com.portfolio.tracker.auth.twofactor.TwoFactorService twoFactorService,
            @Value("${app.auth.refresh-token-days:30}") long refreshTokenDays) {
        this.twoFactorService = twoFactorService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountTokenRepository = accountTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountEmails = accountEmails;
        this.refreshValidity = Duration.ofDays(refreshTokenDays);
    }

    /** Session ouverte : le JWT va dans le corps, le jeton brut de renouvellement dans le cookie. */
    public record Session(String accessToken, long expiresInSeconds, String refreshToken, Duration refreshValidity) {
    }

    /** Appareil à l'origine d'une session (liste des sessions actives). */
    public record Device(String userAgent, String ip) {
        public static final Device UNKNOWN = new Device(null, null);
    }

    /** Connexion : une session, ou un défi de double authentification (jeton de 5 minutes). */
    public record LoginOutcome(Session session, String twoFactorToken) {
    }

    /** Session en cours sur un appareil. */
    public record SessionInfo(UUID id, String userAgent, String ip, LocalDateTime startedAt, LocalDateTime lastUsedAt,
                              boolean current) {
    }

    // ============================================================ inscription

    /** Crée le compte (non vérifié) et envoie le lien de confirmation. Pas de session ouverte. */
    @Transactional
    public UserResponse register(UserCreateRequest request) {
        UserResponse created = userService.create(request);
        User user = userRepository.findById(created.id()).orElseThrow();
        sendVerification(user);
        return created;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AccountToken token = consume(rawToken, AccountTokenType.VERIFY_EMAIL,
                "Lien de confirmation invalide ou expiré : demande un nouvel email.");
        token.getUser().setEmailVerified(true);
    }

    /** Silencieux si l'email est inconnu ou déjà vérifié : ne révèle pas quels comptes existent. */
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerification);
    }

    // =============================================================== sessions

    /** Les exceptions d'échec ne doivent pas annuler les révocations déjà faites. */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session login(String email, String password) {
        LoginOutcome outcome = login(email, password, Device.UNKNOWN);
        if (outcome.session() == null) {
            throw new AuthenticationFailedException("Double authentification requise");
        }
        return outcome.session();
    }

    /**
     * Mot de passe vérifié : session ouverte, ou défi si la double
     * authentification est active (le code se vérifie avec {@link #verifyTwoFactor}).
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginOutcome login(String email, String password, Device device) {
        // BadCredentialsException (→ 401) si email inconnu ou mot de passe faux,
        // sans distinguer les deux cas.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        User user = userRepository.findByEmail(email).orElseThrow();
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (user.isTotpEnabled()) {
            return new LoginOutcome(null, issue(user, AccountTokenType.TWO_FACTOR));
        }
        return new LoginOutcome(openSession(user, device, null), null);
    }

    /** Second facteur : code de l'application ou code de secours. Le défi reste valable en cas d'erreur (5 min). */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session verifyTwoFactor(String rawToken, String code, Device device) {
        LocalDateTime now = LocalDateTime.now();
        AccountToken token = accountTokenRepository.findByTokenHashAndType(SecureTokens.hash(rawToken == null ? "" : rawToken),
                        AccountTokenType.TWO_FACTOR)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new AuthenticationFailedException("Délai dépassé : reconnecte-toi."));
        if (!twoFactorService.check(token.getUser(), code)) {
            throw new AuthenticationFailedException("Code incorrect");
        }
        token.setUsedAt(now);
        return openSession(token.getUser(), device, null);
    }

    /** Sessions en cours de l'utilisateur ; `current` : celle du cookie présenté. */
    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(UUID userId, String rawCurrentToken) {
        String currentHash = rawCurrentToken == null || rawCurrentToken.isBlank() ? null : SecureTokens.hash(rawCurrentToken);
        return refreshTokenRepository.findActive(userId, LocalDateTime.now()).stream()
                .map(t -> new SessionInfo(t.getId(), t.getUserAgent(), t.getIp(),
                        t.getSessionStartedAt() != null ? t.getSessionStartedAt() : t.getCreatedAt(),
                        t.getLastUsedAt() != null ? t.getLastUsedAt() : t.getCreatedAt(),
                        t.getTokenHash().equals(currentHash)))
                .toList();
    }

    /** Déconnecte un appareil. */
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        if (!token.isRevoked()) {
            token.setRevokedAt(LocalDateTime.now());
        }
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session refresh(String rawRefreshToken) {
        return refresh(rawRefreshToken, Device.UNKNOWN);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session refresh(String rawRefreshToken, Device device) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailedException("Session expirée, reconnecte-toi.");
        }
        LocalDateTime now = LocalDateTime.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(SecureTokens.hash(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException("Session expirée, reconnecte-toi."));

        if (token.isRevoked()) {
            if (token.getRevokedAt().isBefore(now.minus(ROTATION_GRACE))) {
                // Jeton déjà remplacé depuis longtemps puis représenté : volé.
                int revoked = refreshTokenRepository.revokeAllForUser(token.getUser().getId(), now);
                log.warn("Réutilisation d'un jeton de renouvellement révoqué pour l'utilisateur {} : "
                        + "{} session(s) révoquée(s)", token.getUser().getId(), revoked);
            }
            throw new AuthenticationFailedException("Session expirée, reconnecte-toi.");
        }
        if (token.isExpired(now)) {
            throw new AuthenticationFailedException("Session expirée, reconnecte-toi.");
        }
        token.setRevokedAt(now);
        // Même session (même appareil) : on garde son début et, à défaut, son navigateur
        return openSession(token.getUser(),
                new Device(device.userAgent() != null ? device.userAgent() : token.getUserAgent(),
                        device.ip() != null ? device.ip() : token.getIp()),
                token.getSessionStartedAt() != null ? token.getSessionStartedAt() : token.getCreatedAt());
    }

    /** Déconnexion de l'appareil courant. Idempotent. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(SecureTokens.hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    // ============================================================ mot de passe

    /** Silencieux si l'email est inconnu : ne révèle pas quels comptes existent. */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user ->
                accountEmails.sendPasswordReset(user, issue(user, AccountTokenType.RESET_PASSWORD)));
    }

    /** Nouveau mot de passe + déconnexion de tous les appareils (le compte a pu être compromis). */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = consume(rawToken, AccountTokenType.RESET_PASSWORD,
                "Lien de réinitialisation invalide ou expiré : refais une demande.");
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        // Cliquer sur le lien reçu par email prouve la possession de l'adresse
        user.setEmailVerified(true);
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now());
    }

    /**
     * Change le mot de passe, révoque toutes les sessions puis en rouvre une
     * pour l'appareil courant (les autres appareils sont déconnectés).
     */
    @Transactional
    public Session changePassword(UUID userId, String currentPassword, String newPassword) {
        return changePassword(userId, currentPassword, newPassword, Device.UNKNOWN);
    }

    @Transactional
    public Session changePassword(UUID userId, String currentPassword, String newPassword, Device device) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
        return openSession(user, device, null);
    }

    /** Session d'un compte invité du mode démo (aucun mot de passe connu). */
    @Transactional
    public Session openDemoSession(User user, Device device) {
        if (!user.isDemo()) {
            throw new IllegalArgumentException("Réservé aux comptes de démonstration");
        }
        return openSession(user, device, null);
    }

    // ================================================================ interne

    /** @param startedAt début de la session (rotation) ; null = nouvelle session */
    private Session openSession(User user, Device device, LocalDateTime startedAt) {
        String raw = SecureTokens.generate();
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(SecureTokens.hash(raw))
                .expiresAt(now.plus(refreshValidity))
                .sessionStartedAt(startedAt != null ? startedAt : now)
                .lastUsedAt(now)
                .userAgent(truncate(device.userAgent(), 255))
                .ip(truncate(device.ip(), 64))
                .build());
        return new Session(jwtService.generateToken(user.getEmail()), jwtService.getExpirationSeconds(),
                raw, refreshValidity);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private void sendVerification(User user) {
        accountEmails.sendVerification(user, issue(user, AccountTokenType.VERIFY_EMAIL));
    }

    /** Émet un lien à usage unique ; les liens précédents du même type deviennent invalides. */
    private String issue(User user, AccountTokenType type) {
        LocalDateTime now = LocalDateTime.now();
        accountTokenRepository.invalidateAll(user.getId(), type, now);
        String raw = SecureTokens.generate();
        accountTokenRepository.save(AccountToken.builder()
                .user(user)
                .type(type)
                .tokenHash(SecureTokens.hash(raw))
                .expiresAt(now.plus(type.validity()))
                .build());
        return raw;
    }

    private AccountToken consume(String rawToken, AccountTokenType type, String invalidMessage) {
        LocalDateTime now = LocalDateTime.now();
        AccountToken token = accountTokenRepository.findByTokenHashAndType(SecureTokens.hash(rawToken), type)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException(invalidMessage));
        token.setUsedAt(now);
        return token;
    }
}

package com.portfolio.tracker.auth.twofactor;

import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.shared.security.SecureTokens;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Double authentification par application (TOTP). Activation en deux temps :
 * secret en attente (QR code), puis confirmation par un premier code. Codes de
 * secours affichés une seule fois. Un même code n'est jamais accepté deux fois.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TwoFactorService {

    static final String ISSUER = "FollowYourMoney";
    static final int RECOVERY_CODES = 8;
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final SecretCipher cipher;
    private final PasswordEncoder passwordEncoder;

    public record Status(boolean enabled, long recoveryCodesLeft) {
    }

    /** @param otpauthUri à mettre en QR code ; {@code secret} pour une saisie manuelle */
    public record Setup(String secret, String otpauthUri) {
    }

    public Status status(UUID userId) {
        User user = user(userId);
        return new Status(user.isTotpEnabled(), user.isTotpEnabled() ? recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId) : 0);
    }

    @Transactional
    public Setup setup(UUID userId) {
        User user = user(userId);
        if (user.isDemo()) {
            throw new BadRequestException("Indisponible en mode démo");
        }
        if (user.isTotpEnabled()) {
            throw new BadRequestException("La double authentification est déjà active");
        }
        String secret = Totp.newSecret();
        user.setTotpPendingSecret(cipher.encrypt(secret));
        String label = URLEncoder.encode(ISSUER + ":" + user.getEmail(), StandardCharsets.UTF_8).replace("+", "%20");
        return new Setup(secret, "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD_SECONDS);
    }

    /** Confirme l'activation avec un premier code ; renvoie les codes de secours (affichés une fois). */
    @Transactional
    public List<String> enable(UUID userId, String code) {
        User user = user(userId);
        if (user.getTotpPendingSecret() == null) {
            throw new BadRequestException("Recommence la configuration : scanne le QR code");
        }
        String secret = cipher.decrypt(user.getTotpPendingSecret());
        long step = Totp.matchingStep(secret, normalize(code), currentStep());
        if (step < 0) {
            throw new BadRequestException("Code incorrect : vérifie l'heure de ton téléphone et réessaie");
        }
        user.setTotpSecret(user.getTotpPendingSecret());
        user.setTotpPendingSecret(null);
        user.setTotpEnabled(true);
        user.setTotpLastStep(step);
        return newRecoveryCodes(userId);
    }

    /** Désactivation : mot de passe + code (ou code de secours). */
    @Transactional
    public void disable(UUID userId, String password, String code) {
        User user = user(userId);
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new BadRequestException("Mot de passe incorrect");
        }
        if (!check(user, code)) {
            throw new BadRequestException("Code incorrect");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpPendingSecret(null);
        user.setTotpLastStep(null);
        recoveryCodeRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, String code) {
        User user = user(userId);
        if (!user.isTotpEnabled() || !check(user, code)) {
            throw new BadRequestException("Code incorrect");
        }
        return newRecoveryCodes(userId);
    }

    /**
     * Vérifie un code de l'application (pas encore utilisé) ou un code de
     * secours (consommé). À appeler dans une transaction d'écriture.
     */
    @Transactional
    public boolean check(User user, String rawCode) {
        if (!user.isTotpEnabled() || rawCode == null) {
            return false;
        }
        String code = normalize(rawCode);
        if (code.matches("\\d{6}")) {
            long step = Totp.matchingStep(cipher.decrypt(user.getTotpSecret()), code, currentStep());
            if (step < 0 || (user.getTotpLastStep() != null && step <= user.getTotpLastStep())) {
                return false; // faux, ou déjà utilisé (rejeu)
            }
            user.setTotpLastStep(step);
            userRepository.save(user);
            return true;
        }
        return recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), SecureTokens.hash(code))
                .map(r -> {
                    r.setUsedAt(LocalDateTime.now());
                    return true;
                }).orElse(false);
    }

    private List<String> newRecoveryCodes(UUID userId) {
        recoveryCodeRepository.deleteAllByUserId(userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                if (j == 5) sb.append('-');
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            codes.add(sb.toString());
            recoveryCodeRepository.save(RecoveryCode.builder().userId(userId).codeHash(SecureTokens.hash(sb.toString())).build());
        }
        return codes;
    }

    /** Espaces retirés (« 123 456 »), minuscules pour les codes de secours. */
    private static String normalize(String code) {
        return code == null ? "" : code.replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    private static long currentStep() {
        return Totp.step(Instant.now().getEpochSecond());
    }

    private User user(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }
}

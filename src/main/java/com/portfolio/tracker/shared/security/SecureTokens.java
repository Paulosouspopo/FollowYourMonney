package com.portfolio.tracker.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Jetons opaques (sessions, liens envoyés par email).
 *
 * Le jeton brut n'est connu que du client ; la base ne stocke que son
 * empreinte SHA-256. Une fuite de la base ne permet donc pas de se faire
 * passer pour un utilisateur. Un hachage lent (bcrypt) est inutile ici :
 * le jeton a 256 bits d'entropie, il n'est pas devinable.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SecureTokens() {
    }

    /** Jeton aléatoire de 256 bits, encodé en base64 URL-safe (utilisable dans un lien). */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Empreinte stockée en base (64 caractères hexadécimaux). */
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}

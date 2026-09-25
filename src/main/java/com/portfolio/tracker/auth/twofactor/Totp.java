package com.portfolio.tracker.auth.twofactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Codes à usage unique basés sur le temps (TOTP, RFC 6238) : HMAC-SHA1, pas de
 * 30 secondes, 6 chiffres — ce qu'attendent Google Authenticator, Authy,
 * 1Password… Calcul pur, JDK seule, testé sur les vecteurs de la RFC.
 */
public final class Totp {

    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Secret de 160 bits, en base32 (à saisir ou scanner dans l'application). */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long step(long epochSeconds) {
        return Math.floorDiv(epochSeconds, PERIOD_SECONDS);
    }

    /** Code d'un pas de temps. */
    public static String code(String base32Secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(fromBase32(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Pas de temps auquel correspond le code (± 1 pas : horloges décalées),
     * ou -1. Comparaison en temps constant.
     */
    public static long matchingStep(String base32Secret, String code, long currentStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        for (long s = currentStep - 1; s <= currentStep + 1; s++) {
            if (java.security.MessageDigest.isEqual(code(base32Secret, s, DIGITS).getBytes(), code.getBytes())) {
                return s;
            }
        }
        return -1;
    }

    public static String base32(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    public static byte[] fromBase32(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Caractère base32 invalide");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }
}

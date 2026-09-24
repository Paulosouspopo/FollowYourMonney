package com.portfolio.tracker.notification.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement des messages Web Push (RFC 8291, encodage aes128gcm de la
 * RFC 8188) et jeton VAPID (RFC 8292), avec la seule JDK : ECDH P-256,
 * HKDF-SHA256, AES-128-GCM, ECDSA P-256.
 *
 * Le navigateur donne à l'abonnement une clé publique ({@code p256dh}) et un
 * secret ({@code auth}) : seul lui peut déchiffrer le message, le service de
 * push (Google, Mozilla, Apple) ne fait que le transporter.
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private WebPushCrypto() {
    }

    // ================================================================= clés

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponible", e);
        }
    }

    /** Point non compressé (0x04 || X || Y, 65 octets), format des clés Web Push. */
    public static byte[] encodePublic(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    public static byte[] encodePrivate(ECPrivateKey key) {
        return fixed32(key.getS());
    }

    public static ECPublicKey decodePublic(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new GeneralSecurityException("Clé publique P-256 non compressée attendue");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256()));
    }

    public static ECPrivateKey decodePrivate(byte[] raw) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), p256()));
    }

    public static KeyPair keyPair(byte[] publicKey, byte[] privateKey) throws GeneralSecurityException {
        return new KeyPair(decodePublic(publicKey), decodePrivate(privateKey));
    }

    // =========================================================== chiffrement

    /** Chiffre avec une clé éphémère et un sel aléatoires (usage normal). */
    public static byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret)
            throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, userAgentPublic, authSecret, generateKeyPair(), salt);
    }

    /**
     * Corps du message chiffré : en-tête (sel, taille d'enregistrement, clé
     * publique éphémère du serveur) suivi d'un unique enregistrement AES-GCM.
     * Paramètres explicites pour les vecteurs de test de la RFC 8291.
     */
    static byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret, KeyPair serverKeys, byte[] salt)
            throws GeneralSecurityException {
        byte[] serverPublic = encodePublic((ECPublicKey) serverKeys.getPublic());

        byte[][] keys = contentKeys(serverKeys, userAgentPublic, authSecret, salt);
        byte[] cek = keys[0];
        byte[] nonce = keys[1];

        // Un seul enregistrement : délimiteur 0x02 (dernier), sans remplissage
        byte[] padded = concat(plaintext, new byte[] { 0x02 });
        if (padded.length + 16 > RECORD_SIZE) {
            throw new GeneralSecurityException("Message push trop long");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic);
        return concat(header.array(), ciphertext);
    }

    /** {clé de contenu (16 octets), nonce (12 octets)} dérivés selon la RFC 8291 §3.4. */
    static byte[][] contentKeys(KeyPair serverKeys, byte[] userAgentPublic, byte[] authSecret, byte[] salt)
            throws GeneralSecurityException {
        byte[] serverPublic = encodePublic((ECPublicKey) serverKeys.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKeys.getPrivate());
        agreement.doPhase(decodePublic(userAgentPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        // IKM = HKDF(auth, ecdh, "WebPush: info" || 0 || ua_public || as_public, 32)
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), userAgentPublic, serverPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        return new byte[][] {
                hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16),
                hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12) };
    }

    // =================================================================== VAPID

    /**
     * En-tête {@code Authorization} VAPID : JWT ES256 signé par la clé du
     * serveur, valable pour l'origine du service de push.
     */
    public static String vapidAuthorization(String endpoint, String subject, KeyPair vapidKeys, long expiresAtEpochSeconds)
            throws GeneralSecurityException {
        java.net.URI uri = java.net.URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtEpochSeconds
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(vapidKeys.getPrivate());
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String jwt = header + "." + claims + "." + B64.encodeToString(signer.sign());
        return "vapid t=" + jwt + ", k=" + B64.encodeToString(encodePublic((ECPublicKey) vapidKeys.getPublic()));
    }

    // ================================================================= utils

    public static String b64(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    public static byte[] unb64(String value) {
        // Les navigateurs peuvent envoyer du base64 standard ou URL-safe, avec ou sans remplissage
        return B64D.decode(value.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    /** HKDF-SHA256 (RFC 5869) pour une sortie ≤ 32 octets. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 0x01);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32); // octet de signe en tête
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}

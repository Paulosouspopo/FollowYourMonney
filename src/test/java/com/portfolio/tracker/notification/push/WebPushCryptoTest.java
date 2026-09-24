package com.portfolio.tracker.notification.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static com.portfolio.tracker.notification.push.WebPushCrypto.b64;
import static com.portfolio.tracker.notification.push.WebPushCrypto.unb64;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Web Push — chiffrement RFC 8291 et VAPID RFC 8292")
class WebPushCryptoTest {

    // Exemple de la RFC 8291, section 5
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";

    @Test
    @DisplayName("Vecteur de la RFC 8291 : le navigateur retrouve le message")
    void vecteurRfc() throws Exception {
        KeyPair server = WebPushCrypto.keyPair(unb64(AS_PUBLIC), unb64(AS_PRIVATE));
        byte[] body = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), unb64(UA_PUBLIC), unb64(AUTH),
                server, unb64(SALT));

        // En-tête : sel, taille d'enregistrement 4096, clé publique du serveur
        assertThat(b64(Arrays.copyOf(body, 16))).isEqualTo(SALT);
        assertThat(b64(Arrays.copyOfRange(body, 21, 86))).isEqualTo(AS_PUBLIC);

        assertThat(decryptAsBrowser(body, unb64(UA_PUBLIC), unb64(UA_PRIVATE), unb64(AUTH))).isEqualTo(PLAINTEXT);

        // Valeurs intermédiaires publiées par la RFC : vérification indépendante de la dérivation
        byte[][] keys = WebPushCrypto.contentKeys(server, unb64(UA_PUBLIC), unb64(AUTH), unb64(SALT));
        assertThat(b64(keys[0])).isEqualTo("oIhVW04MRdy2XN9CiKLxTg");
        assertThat(b64(keys[1])).isEqualTo("4h_95klXJ5E_qnoN");
    }

    @Test
    @DisplayName("Clés et sel aléatoires : aller-retour avec un navigateur simulé")
    void allerRetour() throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] uaPublic = WebPushCrypto.encodePublic((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        byte[] body = WebPushCrypto.encrypt("{\"title\":\"BTC +5 %\"}".getBytes(StandardCharsets.UTF_8), uaPublic, auth);

        byte[] uaPrivate = WebPushCrypto.encodePrivate((java.security.interfaces.ECPrivateKey) browser.getPrivate());
        assertThat(decryptAsBrowser(body, uaPublic, uaPrivate, auth)).isEqualTo("{\"title\":\"BTC +5 %\"}");
    }

    @Test
    @DisplayName("VAPID : JWT ES256 vérifiable avec la clé publique annoncée")
    void vapid() throws Exception {
        KeyPair vapid = WebPushCrypto.generateKeyPair();
        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc", "mailto:test@fym.io",
                vapid, 1_900_000_000L);

        assertThat(header).startsWith("vapid t=").contains(", k=");
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(new String(unb64(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,\"sub\":\"mailto:test@fym.io\"}");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.decodePublic(unb64(header.substring(header.indexOf(", k=") + 4))));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(unb64(parts[2]))).isTrue();
    }

    /** Déchiffrement côté navigateur (RFC 8291 inverse), pour vérifier le format produit. */
    private static String decryptAsBrowser(byte[] body, byte[] uaPublic, byte[] uaPrivate, byte[] auth) throws Exception {
        byte[] salt = Arrays.copyOf(body, 16);
        int keyLength = body[20] & 0xFF;
        byte[] asPublic = Arrays.copyOfRange(body, 21, 21 + keyLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + keyLength, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(WebPushCrypto.decodePrivate(uaPrivate));
        agreement.doPhase(WebPushCrypto.decodePublic(asPublic), true);
        byte[] ecdh = agreement.generateSecret();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = WebPushCrypto.hkdf(auth, ecdh, keyInfo, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        int end = padded.length - 1;
        while (padded[end] == 0) {
            end--;
        }
        assertThat(padded[end]).isEqualTo((byte) 0x02); // délimiteur du dernier enregistrement
        return new String(padded, 0, end, StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}

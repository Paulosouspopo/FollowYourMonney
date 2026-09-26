package com.portfolio.tracker.auth.twofactor;

import com.portfolio.tracker.notification.push.AppSecret;
import com.portfolio.tracker.notification.push.AppSecretRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffre les secrets TOTP en base (AES-256-GCM) : une fuite de la base seule
 * ne permet pas de générer les codes. Clé : `app.security.totp-key` (base64,
 * 32 octets, à fixer en production), sinon générée et gardée dans app_secrets.
 */
@Component
public class SecretCipher {

    private static final String KEY_NAME = "totp.key";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppSecretRepository secrets;
    private final TransactionTemplate tx;
    private final String configured;
    private volatile SecretKeySpec key;

    public SecretCipher(AppSecretRepository secrets, PlatformTransactionManager transactionManager,
            @Value("${app.security.totp-key:}") String configured) {
        this.secrets = secrets;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.configured = configured;
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Déchiffrement impossible", e);
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k == null) {
            synchronized (this) {
                if (key == null) {
                    key = new SecretKeySpec(Base64.getDecoder().decode(load()), "AES");
                }
                k = key;
            }
        }
        return k;
    }

    private String load() {
        if (!configured.isBlank()) {
            return configured;
        }
        return tx.execute(s -> secrets.findById(KEY_NAME).map(AppSecret::getValue).orElseGet(() -> {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String value = Base64.getEncoder().encodeToString(bytes);
            secrets.save(new AppSecret(KEY_NAME, value));
            return value;
        }));
    }
}

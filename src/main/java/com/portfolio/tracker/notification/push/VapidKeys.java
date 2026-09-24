package com.portfolio.tracker.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Clés VAPID : identifient ce serveur auprès des services de push. Fournies
 * en configuration ({@code app.push.vapid-public-key} / {@code -private-key},
 * recommandé en production), sinon générées une fois et conservées en base.
 * Les changer invalide tous les abonnements existants.
 */
@Component
@Slf4j
public class VapidKeys {

    private static final String PUBLIC = "vapid.public";
    private static final String PRIVATE = "vapid.private";

    private final AppSecretRepository secrets;
    private final TransactionTemplate tx;
    private final String configuredPublic;
    private final String configuredPrivate;
    private volatile KeyPair keys;

    public VapidKeys(AppSecretRepository secrets, PlatformTransactionManager transactionManager,
            @Value("${app.push.vapid-public-key:}") String configuredPublic,
            @Value("${app.push.vapid-private-key:}") String configuredPrivate) {
        this.secrets = secrets;
        this.tx = new TransactionTemplate(transactionManager);
        this.configuredPublic = configuredPublic;
        this.configuredPrivate = configuredPrivate;
    }

    public KeyPair keyPair() {
        KeyPair k = keys;
        if (k == null) {
            synchronized (this) {
                if (keys == null) {
                    keys = load();
                }
                k = keys;
            }
        }
        return k;
    }

    /** Clé publique (base64url, point non compressé) transmise au navigateur pour s'abonner. */
    public String publicKey() {
        return WebPushCrypto.b64(WebPushCrypto.encodePublic((ECPublicKey) keyPair().getPublic()));
    }

    private KeyPair load() {
        try {
            if (!configuredPublic.isBlank() && !configuredPrivate.isBlank()) {
                return WebPushCrypto.keyPair(WebPushCrypto.unb64(configuredPublic), WebPushCrypto.unb64(configuredPrivate));
            }
            KeyPair stored = tx.execute(s -> {
                var pub = secrets.findById(PUBLIC);
                var priv = secrets.findById(PRIVATE);
                if (pub.isPresent() && priv.isPresent()) {
                    try {
                        return WebPushCrypto.keyPair(WebPushCrypto.unb64(pub.get().getValue()),
                                WebPushCrypto.unb64(priv.get().getValue()));
                    } catch (GeneralSecurityException e) {
                        throw new IllegalStateException("Clés VAPID en base illisibles", e);
                    }
                }
                KeyPair generated = WebPushCrypto.generateKeyPair();
                secrets.save(new AppSecret(PUBLIC, WebPushCrypto.b64(
                        WebPushCrypto.encodePublic((ECPublicKey) generated.getPublic()))));
                secrets.save(new AppSecret(PRIVATE, WebPushCrypto.b64(
                        WebPushCrypto.encodePrivate((ECPrivateKey) generated.getPrivate()))));
                log.info("Clés VAPID générées et enregistrées (notifications push)");
                return generated;
            });
            return stored;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clés VAPID de la configuration invalides", e);
        }
    }
}

package com.portfolio.tracker.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envoi des notifications push aux appareils d'un utilisateur. Un abonnement
 * refusé définitivement par le service de push (404/410 : appareil
 * désinstallé, permission retirée) est supprimé.
 */
@Service
@Slf4j
public class PushService {

    /** Durée pendant laquelle le service de push garde le message si l'appareil est éteint. */
    private static final Duration TTL = Duration.ofHours(12);

    private final PushSubscriptionRepository repository;
    private final VapidKeys vapidKeys;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final String subject;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public PushService(PushSubscriptionRepository repository, VapidKeys vapidKeys, ObjectMapper json,
            PlatformTransactionManager transactionManager,
            @Value("${app.push.subject:mailto:contact@followyourmoney.local}") String subject) {
        this.repository = repository;
        this.vapidKeys = vapidKeys;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
        // Appelé aussi après le commit d'une notification : ne jamais rejoindre la transaction terminée
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.subject = subject;
    }

    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String deviceLabel) {
        tx.executeWithoutResult(s -> {
            // Un endpoint appartient à un navigateur : s'il change de compte, l'abonnement suit
            PushSubscription sub = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
            sub.setUserId(userId);
            sub.setEndpoint(endpoint);
            sub.setP256dh(p256dh);
            sub.setAuth(auth);
            sub.setDeviceLabel(deviceLabel);
            repository.save(sub);
        });
    }

    public void unsubscribe(UUID userId, String endpoint) {
        tx.executeWithoutResult(s -> repository.deleteByEndpointAndUserId(endpoint, userId));
    }

    public int deviceCount(UUID userId) {
        return repository.findByUserId(userId).size();
    }

    /** @return nombre d'appareils atteints */
    public int send(UUID userId, String title, String body, String link, String tag) {
        List<PushSubscription> subscriptions = tx.execute(s -> repository.findByUserId(userId));
        if (subscriptions == null || subscriptions.isEmpty()) {
            return 0;
        }
        byte[] payload = payload(title, body, link, tag);
        int delivered = 0;
        for (PushSubscription sub : subscriptions) {
            if (sendOne(sub, payload)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean sendOne(PushSubscription sub, byte[] payload) {
        try {
            byte[] encrypted = WebPushCrypto.encrypt(payload, WebPushCrypto.unb64(sub.getP256dh()),
                    WebPushCrypto.unb64(sub.getAuth()));
            String authorization = WebPushCrypto.vapidAuthorization(sub.getEndpoint(), subject, vapidKeys.keyPair(),
                    Instant.now().plus(Duration.ofHours(12)).getEpochSecond());
            HttpRequest request = HttpRequest.newBuilder(URI.create(sub.getEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("TTL", String.valueOf(TTL.toSeconds()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Urgency", "normal")
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                log.info("Abonnement push expiré supprimé ({})", sub.getDeviceLabel());
                tx.executeWithoutResult(s -> repository.deleteById(sub.getId()));
                return false;
            }
            if (status / 100 != 2) {
                log.warn("Push refusé par {} : HTTP {}", URI.create(sub.getEndpoint()).getHost(), status);
                return false;
            }
            tx.executeWithoutResult(s -> repository.findById(sub.getId())
                    .ifPresent(found -> found.setLastSuccessAt(LocalDateTime.now())));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Push non envoyé à {} : {}", sub.getDeviceLabel(), e.getMessage());
            return false;
        }
    }

    /** Contenu lu par le service worker du front (sw.js). Tronqué : 4 Ko maximum chiffré. */
    private byte[] payload(String title, String body, String link, String tag) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("title", title);
        data.put("body", body.length() > 1500 ? body.substring(0, 1499) + "…" : body);
        data.put("link", link != null ? link : "/");
        data.put("tag", tag);
        try {
            return json.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ("{\"title\":\"" + title.replace("\"", "'") + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}

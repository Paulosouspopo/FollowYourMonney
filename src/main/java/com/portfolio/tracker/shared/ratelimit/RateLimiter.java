package com.portfolio.tracker.shared.ratelimit;

import com.portfolio.tracker.shared.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur de débit en mémoire, à fenêtre fixe, pour les endpoints sensibles
 * (connexion, inscription, mot de passe oublié) : freine le bourrage
 * d'identifiants et l'envoi massif d'emails.
 *
 * En mémoire = valable pour UNE instance du backend (notre cas). Avec
 * plusieurs instances, il faudrait un stockage partagé (Redis, base).
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this(enabled, Clock.systemUTC());
    }

    RateLimiter(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    /**
     * Comptabilise une tentative pour {@code key}.
     *
     * @throws TooManyRequestsException si {@code limit} tentatives ont déjà eu
     *                                  lieu dans la fenêtre courante
     */
    public void check(String key, int limit, Duration window) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        Window current = windows.compute(key, (k, w) ->
                w == null || now.isAfter(w.resetAt()) ? new Window(1, now.plus(window)) : w.increment());
        if (current.count() > limit) {
            long retryAfter = Math.max(1, Duration.between(now, current.resetAt()).toSeconds());
            throw new TooManyRequestsException(retryAfter);
        }
    }

    /** Purge des fenêtres expirées : la map ne grossit pas indéfiniment. */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void purgeExpired() {
        Instant now = clock.instant();
        windows.values().removeIf(w -> now.isAfter(w.resetAt()));
    }

    private record Window(int count, Instant resetAt) {
        Window increment() {
            return new Window(count + 1, resetAt);
        }
    }
}

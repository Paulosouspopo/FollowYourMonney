package com.portfolio.tracker.shared.ratelimit;

import com.portfolio.tracker.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RateLimiter — fenêtre fixe en mémoire")
class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** Horloge réglable à la main. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-24T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @Test
    @DisplayName("Bloque au-delà de la limite puis se réinitialise à la fin de la fenêtre")
    void limiteEtReinitialisation() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(true, clock);

        for (int i = 0; i < 3; i++) {
            limiter.check("login:1.2.3.4", 3, WINDOW);
        }
        assertThatThrownBy(() -> limiter.check("login:1.2.3.4", 3, WINDOW))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(900));

        // Clés indépendantes
        assertThatCode(() -> limiter.check("login:5.6.7.8", 3, WINDOW)).doesNotThrowAnyException();

        clock.advance(WINDOW.plusSeconds(1));
        assertThatCode(() -> limiter.check("login:1.2.3.4", 3, WINDOW)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Désactivé : ne bloque jamais")
    void desactive() {
        RateLimiter limiter = new RateLimiter(false, new MutableClock());
        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                limiter.check("k", 1, WINDOW);
            }
        }).doesNotThrowAnyException();
    }
}

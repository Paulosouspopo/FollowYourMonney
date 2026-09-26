package com.portfolio.tracker.auth.twofactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TOTP (RFC 6238)")
class TotpTest {

    /** Secret des vecteurs de test de la RFC (SHA-1) : « 12345678901234567890 ». */
    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    @DisplayName("Vecteurs de la RFC 6238 (8 chiffres) et base32 aller-retour")
    void rfcVectors() {
        assertThat(Totp.code(RFC_SECRET, Totp.step(59), 8)).isEqualTo("94287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1111111109), 8)).isEqualTo("07081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1234567890), 8)).isEqualTo("89005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(2000000000), 8)).isEqualTo("69279037");
        assertThat(new String(Totp.fromBase32(RFC_SECRET), StandardCharsets.US_ASCII)).isEqualTo("12345678901234567890");
        assertThat(Totp.newSecret()).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    @DisplayName("Code accepté à ± 30 secondes, refusé au-delà ou mal formé")
    void window() {
        long step = Totp.step(1_700_000_000L);
        String current = Totp.code(RFC_SECRET, step, 6);
        assertThat(Totp.matchingStep(RFC_SECRET, current, step)).isEqualTo(step);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step - 1, 6), step)).isEqualTo(step - 1);
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step - 2, 6), step)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_SECRET, "12ab56", step)).isEqualTo(-1);
    }
}

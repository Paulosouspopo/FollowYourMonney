package com.portfolio.tracker.auth;

import java.time.Duration;

/** Jetons à usage unique envoyés par email, avec leur durée de validité. */
public enum AccountTokenType {
    VERIFY_EMAIL(Duration.ofHours(48)),
    RESET_PASSWORD(Duration.ofHours(1));

    private final Duration validity;

    AccountTokenType(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}

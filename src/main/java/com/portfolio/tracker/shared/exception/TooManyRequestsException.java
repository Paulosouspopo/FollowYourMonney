package com.portfolio.tracker.shared.exception;

import lombok.Getter;

/** Trop de tentatives sur un endpoint limité. Traduite en HTTP 429 + Retry-After. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Trop de tentatives. Réessaie dans quelques minutes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}

package com.portfolio.tracker.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Objet renvoyé par {@code PushManager.subscribe()} du navigateur (sérialisé en JSON). */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1000) String endpoint,
        @NotNull @Valid Keys keys,
        @Size(max = 200) String deviceLabel
) {
    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {}
}

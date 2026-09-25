package com.portfolio.tracker.notification.dto;

import com.portfolio.tracker.notification.NotificationPreferences;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param quietStart début des heures calmes (0-23), null = aucune
 * @param devices    nombre d'appareils abonnés au push (lecture seule)
 */
public record NotificationPreferencesDto(
        boolean pushEnabled,
        @Min(0) @Max(23) Integer quietStart,
        @Min(0) @Max(23) Integer quietEnd,
        int devices
) {
    public static NotificationPreferencesDto of(NotificationPreferences p, int devices) {
        return new NotificationPreferencesDto(p.isPushEnabled(), p.getQuietStart(), p.getQuietEnd(), devices);
    }
}

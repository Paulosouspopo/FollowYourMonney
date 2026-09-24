package com.portfolio.tracker.notification.dto;

import com.portfolio.tracker.notification.report.ReportSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param sendHour heure d'envoi (0-23), fuseau de Paris */
public record ReportSettingsDto(
        @NotNull(message = "La fréquence est obligatoire")
        ReportSettings.Frequency frequency,
        @Min(value = 0, message = "Heure entre 0 et 23") @Max(value = 23, message = "Heure entre 0 et 23")
        int sendHour,
        boolean notifyEmail
) {
    public static ReportSettingsDto of(ReportSettings s) {
        return new ReportSettingsDto(s.getFrequency(), s.getSendHour(), s.isNotifyEmail());
    }
}

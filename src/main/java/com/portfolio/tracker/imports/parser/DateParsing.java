package com.portfolio.tracker.imports.parser;

import com.portfolio.tracker.shared.TimeZones;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Dates des relevés, converties en heure locale française. */
public final class DateParsing {

    /** Fuseau des utilisateurs : les horodatages UTC (Trade Republic) y sont convertis. */
    public static final ZoneId USER_ZONE = TimeZones.USER_ZONE;

    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private DateParsing() {
    }

    /**
     * Date ou date-heure dans un format courant. Un horodatage avec fuseau
     * (« 2026-02-06T09:56:14.99Z ») est converti en heure de Paris.
     */
    public static Optional<LocalDateTime> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        try {
            return Optional.of(LocalDateTime.ofInstant(Instant.parse(s), USER_ZONE));
        } catch (DateTimeParseException ignored) {
            // pas un instant UTC
        }
        try {
            return Optional.of(OffsetDateTime.parse(s).atZoneSameInstant(USER_ZONE).toLocalDateTime());
        } catch (DateTimeParseException ignored) {
            // pas de décalage horaire
        }
        for (DateTimeFormatter f : DATE_TIMES) {
            try {
                return Optional.of(LocalDateTime.parse(s, f));
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        for (DateTimeFormatter f : DATES) {
            try {
                return Optional.of(LocalDate.parse(s, f).atStartOfDay());
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return Optional.empty();
    }
}

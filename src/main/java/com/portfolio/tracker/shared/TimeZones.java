package com.portfolio.tracker.shared;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Fuseau des utilisateurs (France) : heures d'envoi, horodatages UTC des relevés. */
public final class TimeZones {

    public static final ZoneId USER_ZONE = ZoneId.of("Europe/Paris");

    private TimeZones() {
    }

    public static ZonedDateTime nowForUser() {
        return ZonedDateTime.now(USER_ZONE);
    }

    public static LocalDate todayForUser() {
        return LocalDate.now(USER_ZONE);
    }
}

package com.framework.utils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Date/time helpers for test data that must stay valid relative to "now" (requirement.md
 * &sect;4's target {@code utils/DateUtils}). Currently one job: generating a future
 * event date in the exact shape eventhub's API expects.
 *
 * <p><b>Hardening fix, Phase 14:</b> {@code EventBookingChainingTest}/{@code ApiContextChainingTest}
 * originally hardcoded {@code "2026-12-01T09:00:00.000Z"} as their event date. That is a
 * latent, silent time bomb - not broken today, but it will start failing the moment that
 * literal date is no longer in the future, with a confusing API validation error instead of
 * an obvious "this test data is stale" signal. {@link #futureIsoDate(int)} computes the same
 * shape relative to the actual run time instead, so it never goes stale.</p>
 */
public final class DateUtils {

    private static final DateTimeFormatter EVENTHUB_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private DateUtils() {
    }

    /** An ISO-8601 UTC instant {@code daysFromNow} days from now, e.g. {@code "2026-12-01T09:00:00.000Z"}. */
    public static String futureIsoDate(int daysFromNow) {
        return EVENTHUB_DATE_FORMAT.format(Instant.now().plus(daysFromNow, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
    }
}

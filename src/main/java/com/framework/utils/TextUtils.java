package com.framework.utils;

/**
 * Small, dependency-free text helpers shared across reporting concerns - currently just
 * {@link #humanize(String)}, needed identically by {@code ExtentReportingListener} (the Extent
 * test title) and {@code AllureMetadataListener} (Allure's {@code @Story} label), so it lives
 * here once instead of being copy-pasted between the two (RULE 5).
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * Turns a raw {@code @Test} method identifier (e.g. {@code bookingWithoutAuthReturns401})
     * into a readable phrase ({@code "Booking Without Auth Returns 401"}). Splits at every
     * lower-to-upper-case boundary and every letter-to-digit boundary, then capitalizes the
     * first letter - zero test-author effort, and works for every existing test method without
     * renaming any of them.
     */
    public static String humanize(String identifier) {
        String spaced = identifier
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-zA-Z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([a-zA-Z])", "$1 $2");
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}

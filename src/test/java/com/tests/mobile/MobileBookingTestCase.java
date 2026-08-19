package com.tests.mobile;

/** One row of {@code testdata/json/mobile-booking.json} - see {@link MobileLoginTestCase} for why the id/name/description fields exist. */
public record MobileBookingTestCase(
        String testCaseId,
        String testCaseName,
        String description,
        String fullName,
        String phone) {
}

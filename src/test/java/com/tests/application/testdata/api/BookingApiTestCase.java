package com.tests.application.testdata.api;

import com.tests.tests.api.BookingApiTest;
import com.tests.tests.api.EventBookingE2EFlowTest;
import com.framework.testdata.TestCaseMetadata;
import com.framework.testdata.TestCaseRecord;

/**
 * One row of {@code testdata/json/api/api.json} feeding a {@code /bookings} case in {@link
 * BookingApiTest} (and the booking steps inside {@link EventBookingE2EFlowTest}). Splits
 * {@code metadata} ({@link TestCaseMetadata}) from the actual {@code data} ({@link
 * BookingApiData}), matching the JSON's own shape - see {@link
 * com.tests.application.testdata.LoginTestCase} for the identical shared-shape convention
 * surface. {@code BookingApiData} is nested here rather than its own file - see {@link
 * AuthApiTestCase} for why.
 */
public record BookingApiTestCase(TestCaseMetadata metadata, BookingApiData data) implements TestCaseRecord<BookingApiTestCase.BookingApiData> {

    /**
     * The {@code data} object of one row - see {@link BookingApiTestCase}.
     *
     * <p>{@code quantity}/{@code totalSeats}/{@code page}/{@code limit}/{@code expectedStatusCode}
     * are boxed rather than primitive so a row that doesn't use one (e.g. a customer-validation
     * case has no {@code totalSeats} of its own) leaves it {@code null} instead of a misleading
     * {@code 0}.</p>
     *
     * <p>{@code expectedStatusCode}/{@code expectedError}/{@code expectedMessage}/
     * {@code expectedField}/{@code expectedBookingStatus} are the response-shape assertions each
     * case expects - moved out of {@link com.tests.tests.api.BookingApiTest} and into data so an
     * API contract change (a different status code, a reworded validation message) is a JSON
     * edit, not a Java one, same reasoning as every other field here.</p>
     */
    public record BookingApiData(
            String customerName,
            String customerEmail,
            String customerPhone,
            Integer quantity,
            Integer totalSeats,
            String bookingReference,
            Integer page,
            Integer limit,
            Integer expectedStatusCode,
            String expectedError,
            String expectedMessage,
            String expectedField,
            String expectedBookingStatus) {
    }
}

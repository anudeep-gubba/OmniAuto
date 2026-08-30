package com.tests.application.testdata.api;

import com.tests.steps.api.EventSteps;
import com.tests.steps.api.BookingE2EFlowSteps;
import com.framework.testdata.TestCaseMetadata;
import com.framework.testdata.TestCaseRecord;

/**
 * One row of {@code testdata/json/api/api.json} feeding an {@code /events} payload in {@link
 * EventSteps} (and the event-creation steps inside {@link BookingE2EFlowSteps}). Splits
 * {@code metadata} ({@link TestCaseMetadata}) from the actual {@code data} ({@link
 * EventPayloadData}), matching the JSON's own shape - see {@link
 * com.tests.application.testdata.LoginTestCase} for the identical shared-shape convention
 * surface. {@code EventPayloadData} is nested here rather than its own file - see {@link
 * AuthApiTestCase} for why.
 */
public record EventPayloadTestCase(TestCaseMetadata metadata, EventPayloadData data) implements TestCaseRecord<EventPayloadTestCase.EventPayloadData> {

    /**
     * The {@code data} object of one row - see {@link EventPayloadTestCase}.
     *
     * <p>{@code title} is deliberately not a field here: every event title in these tests is
     * either a fixed literal that carries no data-driven value of its own, or has a per-run
     * unique suffix appended in Java ({@code RandomDataUtils.uniqueId()}) so two parallel runs
     * never collide on the same title - it stays in the Java call site either way. {@code
     * daysInFuture} is resolved via {@code DateUtils.futureIsoDate(int)} at the point of use;
     * {@code eventDate} is only set on rows that instead need a fixed, literal (often past)
     * date, e.g. to trigger the "must be in the future" validation error - the two are mutually
     * exclusive per row.</p>
     *
     * <p>{@code expectedStatusCode}/{@code expectedError}/{@code expectedField}/
     * {@code expectedMessage} are the response-shape assertions each case expects - moved out of
     * {@link EventApiTest}/{@link EventBookingE2EFlowTest} and into data so an API contract
     * change is a JSON edit, not a Java one, same reasoning as {@link BookingApiTestCase.BookingApiData}.
     * A handful of rows (e.g. a bare "unauthenticated create returns 401" case) use only these
     * fields, leaving the event-payload fields above null - same sparse-row convention every
     * other {@code *TestCase} here already follows.</p>
     */
    public record EventPayloadData(
            String eventDescription,
            String category,
            String venue,
            String city,
            Integer daysInFuture,
            String eventDate,
            double price,
            int totalSeats,
            String imageUrl,
            Integer expectedStatusCode,
            String expectedError,
            String expectedField,
            String expectedMessage) {
    }
}

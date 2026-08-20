package com.tests.application.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.tests.application.requests.CreateBookingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Wraps eventhub's {@code /bookings} endpoints - ticket purchases, atomic against the event's
 * seat count. Like {@link EventService}, every endpoint requires authentication and is scoped
 * per-account. {@code cancelBooking} <b>permanently deletes</b> the booking row rather than
 * flagging it {@code cancelled} (verified live: a cancelled booking's {@code GET .../{id}}
 * immediately 404s instead of returning {@code status: "cancelled"}), even though {@code status}
 * still models a {@code cancelled} enum value for filtering an already-fetched list.
 */
public final class BookingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingService.class);

    public ApiResponse createBooking(CreateBookingRequest request) {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/bookings").body(request));
        LOGGER.info("Created booking for event {} (status {})", request.eventId(), response.statusCode());
        return response;
    }

    public ApiResponse getBooking(int bookingId) {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/bookings/{id}").pathParam("id", bookingId));
        LOGGER.info("Fetched booking {} (status {})", bookingId, response.statusCode());
        return response;
    }

    public ApiResponse getBookingByReference(String reference) {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/bookings/ref/{ref}").pathParam("ref", reference));
        LOGGER.info("Fetched booking by reference '{}' (status {})", reference, response.statusCode());
        return response;
    }

    public ApiResponse listBookings() {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/bookings"));
        LOGGER.info("Listed bookings (status {})", response.statusCode());
        return response;
    }

    public ApiResponse listBookingsForEvent(int eventId) {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/bookings").queryParam("eventId", eventId));
        LOGGER.info("Listed bookings for event {} (status {})", eventId, response.statusCode());
        return response;
    }

    /** General-purpose filtering, e.g. {@code Map.of("status", "confirmed", "page", 1, "limit", 5)}. */
    public ApiResponse listBookings(Map<String, Object> filters) {
        ApiRequest request = ApiRequest.get("/bookings");
        filters.forEach(request::queryParam);
        ApiResponse response = ApiClient.execute(request);
        LOGGER.info("Listed bookings with filters {} (status {})", filters, response.statusCode());
        return response;
    }

    public ApiResponse cancelBooking(int bookingId) {
        ApiResponse response = ApiClient.execute(ApiRequest.delete("/bookings/{id}").pathParam("id", bookingId));
        LOGGER.info("Cancelled booking {} (status {})", bookingId, response.statusCode());
        return response;
    }
}

package com.framework.api.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.framework.api.requests.CreateBookingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wraps eventhub's {@code /bookings} endpoints - ticket purchases, atomic against the event's seat count. */
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

    public ApiResponse cancelBooking(int bookingId) {
        ApiResponse response = ApiClient.execute(ApiRequest.delete("/bookings/{id}").pathParam("id", bookingId));
        LOGGER.info("Cancelled booking {} (status {})", bookingId, response.statusCode());
        return response;
    }
}

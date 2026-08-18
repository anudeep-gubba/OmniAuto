package com.framework.api.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.framework.api.requests.CreateBookingRequest;

/** Wraps eventhub's {@code /bookings} endpoints - ticket purchases, atomic against the event's seat count. */
public final class BookingService {

    public ApiResponse createBooking(CreateBookingRequest request) {
        return ApiClient.execute(ApiRequest.post("/bookings").body(request));
    }

    public ApiResponse getBooking(int bookingId) {
        return ApiClient.execute(ApiRequest.get("/bookings/{id}").pathParam("id", bookingId));
    }

    public ApiResponse getBookingByReference(String reference) {
        return ApiClient.execute(ApiRequest.get("/bookings/ref/{ref}").pathParam("ref", reference));
    }

    public ApiResponse listBookings() {
        return ApiClient.execute(ApiRequest.get("/bookings"));
    }

    public ApiResponse listBookingsForEvent(int eventId) {
        return ApiClient.execute(ApiRequest.get("/bookings").queryParam("eventId", eventId));
    }

    public ApiResponse cancelBooking(int bookingId) {
        return ApiClient.execute(ApiRequest.delete("/bookings/{id}").pathParam("id", bookingId));
    }
}

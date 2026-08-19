package com.tests.api.requests;

/** Body for {@code POST /bookings} - buying tickets for an event. */
public record CreateBookingRequest(
        int eventId,
        String customerName,
        String customerEmail,
        String customerPhone,
        int quantity) {
}

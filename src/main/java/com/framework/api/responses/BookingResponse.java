package com.framework.api.responses;

/** A booking as returned by the API, including the atomically-decremented {@link EventResponse} snapshot it embeds. */
public record BookingResponse(
        int id,
        int eventId,
        String customerName,
        String customerEmail,
        String customerPhone,
        int quantity,
        String totalPrice,
        String status,
        String bookingRef,
        String createdAt,
        String updatedAt,
        EventResponse event) {
}

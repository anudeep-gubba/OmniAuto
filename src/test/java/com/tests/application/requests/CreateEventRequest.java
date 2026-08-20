package com.tests.application.requests;

/** Body for {@code POST /events} and {@code PUT /events/{id}}. {@code price} is a JSON number on the request side, unlike {@link com.tests.application.responses.EventResponse#price()} which the API returns as a string (verified live - see Phase 7 summary). */
public record CreateEventRequest(
        String title,
        String description,
        String category,
        String venue,
        String city,
        String eventDate,
        double price,
        int totalSeats,
        String imageUrl) {

    /** Convenience constructor for the common case: no description/imageUrl (both optional per the API schema). */
    public CreateEventRequest(String title, String category, String venue, String city,
                               String eventDate, double price, int totalSeats) {
        this(title, null, category, venue, city, eventDate, price, totalSeats, null);
    }
}

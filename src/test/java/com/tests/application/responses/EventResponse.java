package com.tests.application.responses;

/**
 * An event as returned by the API. {@code price} is modeled as {@code String},
 * not a number: the OpenAPI spec declares it {@code type: number}, but the
 * live API actually returns it as a JSON string (e.g. {@code "100"}) -
 * verified directly, not assumed (see Phase 7 summary). Fields beyond these
 * (e.g. {@code isStatic}, {@code userId}) exist on the live response but
 * aren't modeled here; {@code JsonUtils} tolerates unknown properties.
 */
public record EventResponse(
        int id,
        String title,
        String description,
        String category,
        String venue,
        String city,
        String eventDate,
        String price,
        int totalSeats,
        int availableSeats,
        String imageUrl,
        String createdAt,
        String updatedAt) {
}

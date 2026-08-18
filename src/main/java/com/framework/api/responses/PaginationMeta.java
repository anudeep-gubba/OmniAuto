package com.framework.api.responses;

/** Pagination metadata attached to {@code GET /events} and {@code GET /bookings} list responses. */
public record PaginationMeta(int total, int page, int limit, int totalPages) {
}

package com.tests.api.responses;

import java.util.List;

/** Generic envelope for eventhub's list endpoints, e.g. {@code PaginatedResponse<EventResponse>} for {@code GET /events}. */
public record PaginatedResponse<T>(boolean success, List<T> data, PaginationMeta pagination) {
}

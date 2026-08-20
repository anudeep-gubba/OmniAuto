package com.tests.application.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.tests.application.requests.CreateEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Wraps eventhub's {@code /events} endpoints. All require authentication in practice (verified live - the OpenAPI spec omits {@code security} on most of these, but the API itself returns 401 without a bearer token). Every endpoint is also scoped per-account: one user's events are invisible to another (verified live - a foreign event ID 404s on GET/PUT/DELETE exactly like a nonexistent one). */
public final class EventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventService.class);

    public ApiResponse createEvent(CreateEventRequest request) {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/events").body(request));
        LOGGER.info("Created event '{}' (status {})", request.title(), response.statusCode());
        return response;
    }

    public ApiResponse getEvent(int eventId) {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/events/{id}").pathParam("id", eventId));
        LOGGER.info("Fetched event {} (status {})", eventId, response.statusCode());
        return response;
    }

    public ApiResponse listEvents() {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/events"));
        LOGGER.info("Listed events (status {})", response.statusCode());
        return response;
    }

    public ApiResponse listEvents(int page, int limit) {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/events").queryParam("page", page).queryParam("limit", limit));
        LOGGER.info("Listed events, page {} limit {} (status {})", page, limit, response.statusCode());
        return response;
    }

    /** General-purpose filtering, e.g. {@code Map.of("category", "Conference", "city", "Bangalore", "search", "summit")}. */
    public ApiResponse listEvents(Map<String, Object> filters) {
        ApiRequest request = ApiRequest.get("/events");
        filters.forEach(request::queryParam);
        ApiResponse response = ApiClient.execute(request);
        LOGGER.info("Listed events with filters {} (status {})", filters, response.statusCode());
        return response;
    }

    public ApiResponse updateEvent(int eventId, CreateEventRequest request) {
        ApiResponse response = ApiClient.execute(ApiRequest.put("/events/{id}").pathParam("id", eventId).body(request));
        LOGGER.info("Updated event {} (status {})", eventId, response.statusCode());
        return response;
    }

    public ApiResponse deleteEvent(int eventId) {
        ApiResponse response = ApiClient.execute(ApiRequest.delete("/events/{id}").pathParam("id", eventId));
        LOGGER.info("Deleted event {} (status {})", eventId, response.statusCode());
        return response;
    }
}

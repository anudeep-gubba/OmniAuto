package com.framework.api.services;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.framework.api.requests.CreateEventRequest;

/** Wraps eventhub's {@code /events} endpoints. All require authentication in practice (verified live - the OpenAPI spec omits {@code security} on most of these, but the API itself returns 401 without a bearer token; see Phase 7 summary). */
public final class EventService {

    public ApiResponse createEvent(CreateEventRequest request) {
        return ApiClient.execute(ApiRequest.post("/events").body(request));
    }

    public ApiResponse getEvent(int eventId) {
        return ApiClient.execute(ApiRequest.get("/events/{id}").pathParam("id", eventId));
    }

    public ApiResponse listEvents() {
        return ApiClient.execute(ApiRequest.get("/events"));
    }

    public ApiResponse listEvents(int page, int limit) {
        return ApiClient.execute(ApiRequest.get("/events").queryParam("page", page).queryParam("limit", limit));
    }

    public ApiResponse updateEvent(int eventId, CreateEventRequest request) {
        return ApiClient.execute(ApiRequest.put("/events/{id}").pathParam("id", eventId).body(request));
    }

    public ApiResponse deleteEvent(int eventId) {
        return ApiClient.execute(ApiRequest.delete("/events/{id}").pathParam("id", eventId));
    }
}

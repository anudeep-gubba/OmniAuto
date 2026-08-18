package com.framework.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.framework.exceptions.ApiException;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.utils.JsonUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/**
 * Wraps REST Assured's {@link Response} with the operations tests actually
 * need - status code, typed extraction, assertion - so test/service code
 * never touches REST Assured's own response type directly (RULE 12).
 * {@link #raw()} is the escape hatch for anything not covered here (schema
 * validation, response time assertions, ...).
 */
public final class ApiResponse {

    private final Response response;

    ApiResponse(Response response) {
        this.response = response;
    }

    public int statusCode() {
        return response.getStatusCode();
    }

    public String body() {
        return response.getBody().asString();
    }

    public JsonPath jsonPath() {
        return response.jsonPath();
    }

    public String header(String name) {
        return response.getHeader(name);
    }

    public <T> T as(Class<T> type) {
        return JsonUtils.fromJson(body(), type);
    }

    /**
     * For APIs (like eventhub's) that wrap a resource in an envelope such as
     * {@code {success, data, message}}: deserializes just the node at
     * {@code dotSeparatedPath} (e.g. {@code "data"}) into {@code type}, rather
     * than the whole body. Using {@link #as(Class)} on an enveloped body
     * silently produces a zero/null-filled object instead of failing loudly
     * (found in practice - see Phase 7 summary), because every declared
     * field is genuinely absent at the document root.
     */
    public <T> T extract(String dotSeparatedPath, Class<T> type) {
        return JsonUtils.fromJsonAtPath(body(), dotSeparatedPath, type);
    }

    /** For generic response types erasure defeats {@link #as(Class)} on, e.g. {@code PaginatedResponse<EventResponse>}. */
    public <T> T as(TypeReference<T> typeReference) {
        return JsonUtils.fromJson(body(), typeReference);
    }

    public ApiResponse assertStatusCode(int expected) {
        if (statusCode() != expected) {
            // Masked (RULE 7/19): this exception message is the most broadly-used failure
            // surface in the whole framework - it must not leak a secret an error response
            // happens to echo back, same as ApiClient's own request/response logging already does.
            throw new ApiException("Expected status code " + expected + " but got " + statusCode()
                    + ". Body: " + SensitiveDataMasker.mask(body()));
        }
        return this;
    }

    public Response raw() {
        return response;
    }
}

package com.framework.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the header set for a request: framework defaults, the current
 * thread's bearer token (if any - see {@link ApiClient#setAuthToken(String)}),
 * then the request's own headers layered on top so an explicit
 * {@code Authorization} header on a specific call always wins.
 */
final class ApiHeaders {

    private ApiHeaders() {
    }

    static Map<String, String> build(Map<String, String> requestHeaders, String currentThreadToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        if (currentThreadToken != null) {
            headers.put("Authorization", "Bearer " + currentThreadToken);
        }
        headers.putAll(requestHeaders);
        return headers;
    }
}

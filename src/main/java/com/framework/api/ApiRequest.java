package com.framework.api;

import io.restassured.http.Method;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fluent, framework-level description of one API call - method, endpoint,
 * headers, query/path params, body - handed to {@link ApiClient#execute(ApiRequest)}.
 * Test/service code builds one of these instead of touching REST Assured's
 * own {@code RequestSpecification} directly (RULE 12).
 *
 * <p>Built fresh per call and not thread-shared, so its internal mutability
 * is not a thread-safety concern.</p>
 */
public final class ApiRequest {

    private final Method method;
    private final String endpoint;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> queryParams = new LinkedHashMap<>();
    private final Map<String, Object> pathParams = new LinkedHashMap<>();
    private Object body;

    private ApiRequest(Method method, String endpoint) {
        this.method = method;
        this.endpoint = endpoint;
    }

    public static ApiRequest get(String endpoint) {
        return new ApiRequest(Method.GET, endpoint);
    }

    public static ApiRequest post(String endpoint) {
        return new ApiRequest(Method.POST, endpoint);
    }

    public static ApiRequest put(String endpoint) {
        return new ApiRequest(Method.PUT, endpoint);
    }

    public static ApiRequest patch(String endpoint) {
        return new ApiRequest(Method.PATCH, endpoint);
    }

    public static ApiRequest delete(String endpoint) {
        return new ApiRequest(Method.DELETE, endpoint);
    }

    public ApiRequest header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public ApiRequest queryParam(String name, Object value) {
        queryParams.put(name, value);
        return this;
    }

    /** {@code endpoint} must contain a matching {@code {name}} placeholder, e.g. {@code "/events/{id}"}. */
    public ApiRequest pathParam(String name, Object value) {
        pathParams.put(name, value);
        return this;
    }

    public ApiRequest body(Object body) {
        this.body = body;
        return this;
    }

    Method method() {
        return method;
    }

    String endpoint() {
        return endpoint;
    }

    Map<String, String> headers() {
        return headers;
    }

    Map<String, Object> queryParams() {
        return queryParams;
    }

    Map<String, Object> pathParams() {
        return pathParams;
    }

    Object body() {
        return body;
    }
}

package com.tests.api.responses;

/** Body of an error response, e.g. {@code {"success":false,"error":"Event not found"}}. */
public record ErrorResponseBody(boolean success, String error) {
}

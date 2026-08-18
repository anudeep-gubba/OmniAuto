package com.framework.api;

import io.restassured.module.jsv.JsonSchemaValidator;

/**
 * API-specific utilities that don't belong on {@link ApiRequest}/{@link ApiResponse}
 * themselves. Currently just JSON schema validation (requirement.md &sect;9:
 * "schema validation where appropriate"); deliberately not padded with
 * speculative helpers (RULE 15).
 */
public final class ApiUtils {

    private ApiUtils() {
    }

    /** Validates a response body against a JSON schema file on the test classpath (e.g. {@code src/test/resources/schemas/event.json}). */
    public static void assertMatchesSchema(ApiResponse response, String classpathSchemaFile) {
        response.raw().then().assertThat().body(JsonSchemaValidator.matchesJsonSchemaInClasspath(classpathSchemaFile));
    }
}

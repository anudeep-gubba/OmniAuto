package com.framework.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exceptions.ApiException;

/**
 * Shared JSON (de)serialization for the framework: application-specific request/response DTOs
 * ({@code com.tests.application.requests}/{@code com.tests.application.responses} - src/test, Phase 7) today,
 * JSON test-data files ({@code JsonDataReader}, Phase 9) later - one
 * {@link ObjectMapper}, one configuration, instead of each caller building
 * its own (RULE 5).
 *
 * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} is disabled: real APIs return fields
 * beyond what their own published schema documents (found in practice
 * against eventhub's API - see Phase 7 summary), and a test framework
 * should tolerate additive API changes rather than break on them.</p>
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new ApiException("Failed to deserialize JSON into " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** For generic types erasure defeats {@link #fromJson(String, Class)} on, e.g. {@code PaginatedResponse<EventResponse>}. */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new ApiException("Failed to deserialize JSON into " + typeReference.getType() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Navigates a dot-separated path (e.g. {@code "data"}, {@code "data.event"}) into a JSON
     * document and deserializes the node found there into {@code type} - for APIs like
     * eventhub's that wrap a resource in an envelope ({@code {success, data, message}}), where
     * the domain object a response/request record models is nested, not at the document root
     * (found in practice deserializing {@code BookingResponse} - see Phase 7 summary; this uses
     * this class's own {@link #OBJECT_MAPPER}, not REST Assured's separate internal one, so
     * unknown-property tolerance stays consistent everywhere).
     */
    public static <T> T fromJsonAtPath(String json, String dotSeparatedPath, Class<T> type) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            for (String segment : dotSeparatedPath.split("\\.")) {
                node = node.path(segment);
            }
            if (node.isMissingNode()) {
                throw new ApiException("No node found at path '" + dotSeparatedPath + "' in: " + json);
            }
            return OBJECT_MAPPER.treeToValue(node, type);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to deserialize node at path '" + dotSeparatedPath
                    + "' into " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new ApiException("Failed to serialize " + value.getClass().getSimpleName() + " to JSON: " + e.getMessage(), e);
        }
    }

    public static ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }
}

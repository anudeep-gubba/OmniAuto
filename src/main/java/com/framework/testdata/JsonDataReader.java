package com.framework.testdata;

import com.framework.utils.JsonUtils;

/**
 * Reads {@code .json} test data. Reuses {@link JsonUtils#objectMapper()} rather than a
 * second {@code ObjectMapper} instance (RULE 5) - this is pure structural parsing (JSON
 * text to {@code Map}/{@code List}), so the API-response mapper's unknown-property
 * tolerance is irrelevant either way. Root-shape handling lives in
 * {@link JacksonTreeDataReader}, shared with {@link YamlDataReader}.
 */
final class JsonDataReader extends JacksonTreeDataReader {

    JsonDataReader() {
        super(JsonUtils.objectMapper(), "JSON");
    }
}

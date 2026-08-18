package com.framework.testdata;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Reads {@code .yaml}/{@code .yml} test data. YAML is structurally a superset of JSON, so
 * this parses into the exact same Jackson tree model as {@link JsonDataReader} via a
 * YAML-specific {@link ObjectMapper} - only the factory differs; root-shape handling lives
 * in the shared {@link JacksonTreeDataReader}.
 */
final class YamlDataReader extends JacksonTreeDataReader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    YamlDataReader() {
        super(YAML_MAPPER, "YAML");
    }
}

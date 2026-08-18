package com.framework.testdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exceptions.TestDataException;
import com.framework.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared record-normalization logic for {@link JsonDataReader} and {@link YamlDataReader}:
 * both parse into Jackson's same {@link JsonNode} tree model (YAML is structurally a
 * superset of JSON), so only the {@link ObjectMapper}/factory differs between the two
 * (RULE 5 - no duplicated traversal logic).
 *
 * <p>Accepts two root shapes (see {@link TestDataReader}):</p>
 * <pre>
 * // Array root -&gt; each element is one record, unnamed unless it has its own "name" field.
 * [ {"username": "a"}, {"username": "b"} ]
 *
 * // Object root -&gt; each entry is one record, named after its key.
 * { "validLogin": {"username": "${{LOGIN_USERNAME}}", "password": "${{LOGIN_PASSWORD}}"},
 *   "invalidLogin": {"username": "wrong", "password": "wrong"} }
 * </pre>
 */
abstract class JacksonTreeDataReader implements TestDataReader {

    private final ObjectMapper mapper;
    private final String formatName;

    protected JacksonTreeDataReader(ObjectMapper mapper, String formatName) {
        this.mapper = mapper;
        this.formatName = formatName;
    }

    @Override
    public final List<Map<String, Object>> read(String classpathResource) {
        JsonNode root;
        try (InputStream in = FileUtils.openClasspathResource(classpathResource)) {
            root = mapper.readTree(in);
        } catch (IOException e) {
            throw new TestDataException("Failed to read/parse " + formatName + " test data file '" + classpathResource + "'.", e);
        }

        if (root.isArray()) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (JsonNode element : root) {
                records.add(toRecord(element, classpathResource));
            }
            return records;
        }
        if (root.isObject()) {
            List<Map<String, Object>> records = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                Map<String, Object> record = toRecord(field.getValue(), classpathResource);
                record.putIfAbsent(NAME_FIELD, field.getKey());
                records.add(record);
            }
            return records;
        }
        throw new TestDataException(
                formatName + " test data file '" + classpathResource + "' must have an object or array at its root, "
                        + "but found: " + root.getNodeType());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toRecord(JsonNode node, String classpathResource) {
        if (!node.isObject()) {
            throw new TestDataException(
                    "Each record in '" + classpathResource + "' must be an object, but found: " + node.getNodeType());
        }
        return mapper.convertValue(node, LinkedHashMap.class);
    }
}

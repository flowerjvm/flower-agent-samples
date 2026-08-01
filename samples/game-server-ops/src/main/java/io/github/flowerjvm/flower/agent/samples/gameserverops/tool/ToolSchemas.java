package io.github.flowerjvm.flower.agent.samples.gameserverops.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ToolSchemas {

    private ToolSchemas() {
    }

    static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return Map.copyOf(schema);
    }

    static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> integer(String description, int minimum, int maximum) {
        return Map.of(
                "type", "integer",
                "description", description,
                "minimum", minimum,
                "maximum", maximum);
    }
}

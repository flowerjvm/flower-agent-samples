package io.github.flowerjvm.flower.agent.samples.refundops.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    public static Map<String, Object> integer(String description, long minimum) {
        return Map.of("type", "integer", "description", description, "minimum", minimum);
    }

    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(properties));
        schema.put("required", List.of(required));
        schema.put("additionalProperties", false);
        return schema;
    }
}

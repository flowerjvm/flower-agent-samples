package io.github.flowerjvm.flower.agent.samples.gameserverops.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.model.ToolDefinition;
import io.github.flowerjvm.flower.agent.model.ToolResult;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.tool.AgentTool;
import io.github.flowerjvm.flower.agent.tool.AgentToolContext;
import io.github.flowerjvm.flower.agent.tool.AgentToolExecution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class SearchServerLogsTool implements AgentTool {

    public static final String TOOL_NAME = "game.server.logs.search";

    private final GameServerFleet fleet;
    private final ObjectMapper objectMapper;
    private final ToolDefinition definition;

    public SearchServerLogsTool(GameServerFleet fleet, ObjectMapper objectMapper) {
        this.fleet = fleet;
        this.objectMapper = objectMapper;
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("serverId", ToolSchemas.string("Game server id such as server-alpha."));
        properties.put("query", ToolSchemas.string("Optional text or level to search for."));
        properties.put("limit", ToolSchemas.integer("Maximum returned log entries.", 1, 50));
        this.definition = new ToolDefinition(
                TOOL_NAME,
                "Search recent logs for one game server.",
                ToolSchemas.object(properties, "serverId"));
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public AgentToolExecution start(ToolCall call, AgentToolContext context) {
        return new FutureAgentToolExecution(call.callId(), CompletableFuture.completedFuture(search(call)));
    }

    private ToolResult search(ToolCall call) {
        try {
            String serverId = String.valueOf(call.arguments().get("serverId"));
            String query = String.valueOf(call.arguments().getOrDefault("query", ""));
            int limit = call.arguments().get("limit") instanceof Number number ? number.intValue() : 10;
            return ToolResult.succeeded(
                    call.callId(),
                    TOOL_NAME,
                    objectMapper.writeValueAsString(Map.of(
                            "serverId", serverId,
                            "entries", fleet.searchLogs(serverId, query, limit))));
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            return ToolResult.failed(call.callId(), TOOL_NAME, "LOG_SEARCH_FAILED", exception.getMessage());
        }
    }
}

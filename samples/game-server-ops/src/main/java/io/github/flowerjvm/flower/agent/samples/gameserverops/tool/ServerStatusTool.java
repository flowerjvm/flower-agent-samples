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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ServerStatusTool implements AgentTool {

    public static final String TOOL_NAME = "game.server.status";

    private final GameServerFleet fleet;
    private final ObjectMapper objectMapper;
    private final ToolDefinition definition = new ToolDefinition(
            TOOL_NAME,
            "Read the current operational state of one game server.",
            ToolSchemas.object(Map.of(
                    "serverId", ToolSchemas.string("Game server id such as server-alpha.")), "serverId"));

    public ServerStatusTool(GameServerFleet fleet, ObjectMapper objectMapper) {
        this.fleet = fleet;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public AgentToolExecution start(ToolCall call, AgentToolContext context) {
        return new FutureAgentToolExecution(call.callId(), CompletableFuture.completedFuture(read(call)));
    }

    private ToolResult read(ToolCall call) {
        try {
            String serverId = String.valueOf(call.arguments().get("serverId"));
            return ToolResult.succeeded(call.callId(), TOOL_NAME, objectMapper.writeValueAsString(fleet.server(serverId)));
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            return ToolResult.failed(call.callId(), TOOL_NAME, "SERVER_STATUS_FAILED", exception.getMessage());
        }
    }
}

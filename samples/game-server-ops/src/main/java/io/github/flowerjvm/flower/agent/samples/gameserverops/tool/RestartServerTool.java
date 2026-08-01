package io.github.flowerjvm.flower.agent.samples.gameserverops.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.action.runtime.ActionExecutionResult;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.model.ToolDefinition;
import io.github.flowerjvm.flower.agent.model.ToolResult;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RestartGameServerAction;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.ActionAttemptView;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskRegistry;
import io.github.flowerjvm.flower.agent.tool.AgentTool;
import io.github.flowerjvm.flower.agent.tool.AgentToolContext;
import io.github.flowerjvm.flower.agent.tool.AgentToolExecution;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class RestartServerTool implements AgentTool {

    public static final String TASK_ID_METADATA = "sample.taskId";
    public static final String TOOL_NAME = RestartGameServerAction.ACTION_ID;

    private final DefaultActionRuntime runtime;
    private final GameOpsTaskRegistry taskRegistry;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Clock clock;
    private final ToolDefinition definition;

    public RestartServerTool(
            DefaultActionRuntime runtime,
            GameOpsTaskRegistry taskRegistry,
            ObjectMapper objectMapper,
            Executor executor,
            Clock clock
    ) {
        this.runtime = runtime;
        this.taskRegistry = taskRegistry;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.clock = clock;
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("serverId", ToolSchemas.string("Degraded game server to restart."));
        properties.put("reason", ToolSchemas.string("Evidence-based operational reason for the restart."));
        this.definition = new ToolDefinition(
                TOOL_NAME,
                "Request a governed restart of one degraded game server.",
                ToolSchemas.object(properties, "serverId", "reason"));
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public AgentToolExecution start(ToolCall call, AgentToolContext context) {
        CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> execute(call, context), executor);
        return new FutureAgentToolExecution(call.callId(), future);
    }

    private ToolResult execute(ToolCall call, AgentToolContext toolContext) {
        String taskId = String.valueOf(toolContext.metadata().getOrDefault(TASK_ID_METADATA, ""));
        if (taskId.isBlank()) {
            return ToolResult.failed(call.callId(), TOOL_NAME, "TASK_SCOPE_MISSING",
                    "The host did not provide a stable task scope.");
        }
        String serverId = String.valueOf(call.arguments().get("serverId"));
        String reason = String.valueOf(call.arguments().getOrDefault("reason", "agent-requested recovery"));
        String actionRunId = UUID.randomUUID().toString();
        String idempotencyKey = RestartGameServerAction.idempotencyKey(taskId, serverId);

        ActionProposal proposal = ActionProposal.builder(RestartGameServerAction.ACTION_ID)
                .requestChannel(ActionRequestChannel.COMMAND)
                .proposerType(ActionProposerType.AI_PLANNER)
                .requesterId("game-server-ops-agent")
                .reason(reason)
                .confidence(0.9d)
                .input(Map.of("serverId", serverId, "reason", reason))
                .idempotencyKey(idempotencyKey)
                .metadata(Map.of("taskId", taskId, "agentRunId", toolContext.runId()))
                .build();
        ExecutionContext actionContext = new ExecutionContext(
                "sample",
                "game-server-ops-agent",
                actionRunId,
                taskId,
                Map.of(
                        "actor.roles", java.util.List.of("GAME_OPERATOR"),
                        "resource.type", "game-server",
                        "resource.id", serverId,
                        "taskId", taskId));

        ActionExecutionResult result = runtime.handle(proposal, actionContext);
        taskRegistry.recordAction(taskId, new ActionAttemptView(
                actionRunId,
                RestartGameServerAction.ACTION_ID,
                serverId,
                result.status(),
                result.code(),
                result.message(),
                result.output(),
                clock.instant()));
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("actionRunId", actionRunId);
            payload.put("status", result.status().name());
            payload.put("code", result.code());
            payload.put("output", result.output());
            String content = objectMapper.writeValueAsString(payload);
            return result.terminalSuccess()
                    ? ToolResult.succeeded(call.callId(), TOOL_NAME, content)
                    : ToolResult.failed(call.callId(), TOOL_NAME, result.code(), content);
        } catch (JsonProcessingException exception) {
            return ToolResult.failed(call.callId(), TOOL_NAME, "ACTION_RESULT_ENCODING_FAILED", exception.getMessage());
        }
    }
}

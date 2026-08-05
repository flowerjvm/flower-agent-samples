package io.github.flowerjvm.flower.agent.samples.refundops.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.action.runtime.ActionExecutionResult;
import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.model.ToolDefinition;
import io.github.flowerjvm.flower.agent.model.ToolResult;
import io.github.flowerjvm.flower.agent.samples.refundops.action.IssueRefundAction;
import io.github.flowerjvm.flower.agent.samples.refundops.task.ActionAttemptView;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.TraceCorrelationRegistry;
import io.github.flowerjvm.flower.agent.tool.AgentTool;
import io.github.flowerjvm.flower.agent.tool.AgentToolContext;
import io.github.flowerjvm.flower.agent.tool.AgentToolExecution;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class IssueRefundTool implements AgentTool {

    public static final String TASK_ID_METADATA = "sample.taskId";
    public static final String TOOL_NAME = IssueRefundAction.ACTION_ID;

    private final DefaultActionRuntime runtime;
    private final RefundTaskRegistry tasks;
    private final TraceCorrelationRegistry correlations;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Clock clock;
    private final ToolDefinition definition;

    public IssueRefundTool(
            DefaultActionRuntime runtime,
            RefundTaskRegistry tasks,
            TraceCorrelationRegistry correlations,
            ObjectMapper objectMapper,
            Executor executor,
            Clock clock
    ) {
        this.runtime = runtime;
        this.tasks = tasks;
        this.correlations = correlations;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.clock = clock;
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("orderId", ToolSchemas.string("Eligible order to refund."));
        properties.put("amount", ToolSchemas.integer("Exact refundable amount in the order currency.", 1L));
        properties.put("reason", ToolSchemas.string("Evidence-based reason for the refund."));
        this.definition = new ToolDefinition(
                TOOL_NAME,
                "Request a governed refund for one eligible order.",
                ToolSchemas.object(properties, "orderId", "amount", "reason"));
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public AgentToolExecution start(ToolCall call, AgentToolContext context) {
        CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(
                () -> execute(call, context), executor);
        return new FutureAgentToolExecution(call.callId(), future);
    }

    private ToolResult execute(ToolCall call, AgentToolContext toolContext) {
        String taskId = String.valueOf(toolContext.metadata().getOrDefault(TASK_ID_METADATA, ""));
        if (taskId.isBlank()) {
            return ToolResult.failed(
                    call.callId(), TOOL_NAME, "TASK_SCOPE_MISSING", "The host did not provide a task scope.");
        }
        String orderId = String.valueOf(call.arguments().get("orderId"));
        Object rawAmount = call.arguments().get("amount");
        long amount = rawAmount instanceof Number number ? number.longValue() : -1L;
        String reason = String.valueOf(call.arguments().getOrDefault("reason", "customer refund request"));
        String actionRunId = UUID.randomUUID().toString();
        correlations.register(actionRunId, taskId, toolContext.runId());

        ActionProposal proposal = ActionProposal.builder(IssueRefundAction.ACTION_ID)
                .requestChannel(ActionRequestChannel.COMMAND)
                .proposerType(ActionProposerType.AI_PLANNER)
                .requesterId("refund-ops-agent")
                .reason(reason)
                .confidence(0.9d)
                .input(Map.of("orderId", orderId, "amount", amount, "reason", reason))
                .idempotencyKey(IssueRefundAction.idempotencyKey(taskId, orderId))
                .metadata(Map.of("taskId", taskId, "agentRunId", toolContext.runId()))
                .build();
        ExecutionContext actionContext = new ExecutionContext(
                "sample",
                "refund-ops-agent",
                actionRunId,
                taskId,
                Map.of(
                        "actor.roles", List.of("REFUND_OPERATOR"),
                        "resource.type", "order",
                        "resource.id", orderId,
                        "taskId", taskId));

        ActionExecutionResult result = runtime.handle(proposal, actionContext);
        tasks.recordAction(taskId, new ActionAttemptView(
                actionRunId,
                IssueRefundAction.ACTION_ID,
                orderId,
                result.status(),
                result.code(),
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
            return ToolResult.failed(
                    call.callId(), TOOL_NAME, "ACTION_RESULT_ENCODING_FAILED", exception.getMessage());
        }
    }
}

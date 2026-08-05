package io.github.flowerjvm.flower.agent.samples.refundops.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.model.ToolDefinition;
import io.github.flowerjvm.flower.agent.model.ToolResult;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.tool.AgentTool;
import io.github.flowerjvm.flower.agent.tool.AgentToolContext;
import io.github.flowerjvm.flower.agent.tool.AgentToolExecution;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CheckRefundPolicyTool implements AgentTool {

    public static final String TOOL_NAME = "commerce.refund.policy.check";

    private final OrderStore orders;
    private final ObjectMapper objectMapper;
    private final ToolDefinition definition = new ToolDefinition(
            TOOL_NAME,
            "Evaluate the host-owned refund rules for one order without changing it.",
            ToolSchemas.object(Map.of(
                    "orderId", ToolSchemas.string("Order id to evaluate.")), "orderId"));

    public CheckRefundPolicyTool(OrderStore orders, ObjectMapper objectMapper) {
        this.orders = orders;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public AgentToolExecution start(ToolCall call, AgentToolContext context) {
        return new FutureAgentToolExecution(call.callId(), CompletableFuture.completedFuture(check(call)));
    }

    private ToolResult check(ToolCall call) {
        try {
            String orderId = String.valueOf(call.arguments().get("orderId"));
            return ToolResult.succeeded(
                    call.callId(), TOOL_NAME, objectMapper.writeValueAsString(orders.eligibility(orderId)));
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            return ToolResult.failed(call.callId(), TOOL_NAME, "REFUND_POLICY_CHECK_FAILED", exception.getMessage());
        }
    }
}

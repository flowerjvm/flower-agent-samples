package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.agent.gateway.AgentModelCall;
import io.github.flowerjvm.flower.agent.gateway.AgentModelCallStatus;
import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.agent.model.AgentMessage;
import io.github.flowerjvm.flower.agent.model.AgentModelRequest;
import io.github.flowerjvm.flower.agent.model.AgentModelResponse;
import io.github.flowerjvm.flower.agent.model.AgentRole;
import io.github.flowerjvm.flower.agent.model.AgentUsage;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.CheckRefundPolicyTool;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.GetOrderTool;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.IssueRefundTool;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic model that leaves every runtime and Tool path real. */
final class RefundOpsEvaluationScriptedModelGateway implements AgentModelGateway {

    private final Clock clock;
    private final AtomicInteger callIds = new AtomicInteger();

    RefundOpsEvaluationScriptedModelGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public AgentModelCall submit(AgentModelRequest request) {
        String orderId = String.valueOf(request.metadata().get("sample.orderId"));
        long toolResults = request.messages().stream()
                .filter(message -> message.role() == AgentRole.TOOL)
                .count();
        AgentModelResponse response;
        if (toolResults == 0L) {
            response = toolResponse(
                    "order", GetOrderTool.TOOL_NAME, Map.of("orderId", orderId));
        } else if (toolResults == 1L) {
            response = toolResponse(
                    "policy", CheckRefundPolicyTool.TOOL_NAME, Map.of("orderId", orderId));
        } else if ("order-1001".equals(orderId) && toolResults == 2L) {
            response = toolResponse(
                    "refund",
                    IssueRefundTool.TOOL_NAME,
                    Map.of(
                            "orderId", orderId,
                            "amount", 54_000L,
                            "reason", "Delivered within the automatic refund window"));
        } else if ("order-1001".equals(orderId) && toolResults == 3L) {
            response = toolResponse(
                    "verify", GetOrderTool.TOOL_NAME, Map.of("orderId", orderId));
        } else {
            response = finalResponse(request, orderId);
        }
        return new ImmediateCall("refund-evaluation-" + callIds.incrementAndGet(), response);
    }

    private AgentModelResponse toolResponse(
            String idPrefix,
            String toolName,
            Map<String, Object> arguments
    ) {
        ToolCall call = new ToolCall(
                idPrefix + "-" + callIds.incrementAndGet(),
                toolName,
                arguments);
        return new AgentModelResponse(
                AgentMessage.assistant("", List.of(call), clock.instant()),
                List.of(call),
                new AgentUsage(30, 8),
                "tool_calls",
                Map.of("evaluation.mode", "scripted"));
    }

    private AgentModelResponse finalResponse(AgentModelRequest request, String orderId) {
        String taskId = String.valueOf(request.metadata().get(IssueRefundTool.TASK_ID_METADATA));
        String content;
        if ("order-1001".equals(orderId)) {
            content = """
                    {"taskId":"%s","orderId":"order-1001","outcome":"REFUNDED",
                    "summary":"The eligible order was refunded through the governed action.",
                    "evidence":["Policy code ELIGIBLE","Final order status REFUNDED"],
                    "actions":[{"actionId":"commerce.refund.issue","status":"SUCCEEDED",
                    "reason":"Delivered within the automatic refund window"}],
                    "finalState":{"orderId":"order-1001","status":"REFUNDED",
                    "refundedAmount":54000,"currency":"KRW"},"residualRisks":[]}
                    """.formatted(taskId);
        } else if ("order-1003".equals(orderId)) {
            content = """
                    {"taskId":"%s","orderId":"order-1003","outcome":"MANUAL_REVIEW",
                    "summary":"The amount exceeds the automatic refund limit.",
                    "evidence":["Policy code MANUAL_REVIEW_REQUIRED"],"actions":[],
                    "finalState":{"orderId":"order-1003","status":"DELIVERED",
                    "refundedAmount":0,"currency":"KRW"},
                    "residualRisks":["A human refund decision is still required"]}
                    """.formatted(taskId);
        } else {
            content = """
                    {"taskId":"%s","orderId":"order-1002","outcome":"NO_ACTION_NEEDED",
                    "summary":"The refund window expired, so no automatic action was taken.",
                    "evidence":["Policy code REFUND_WINDOW_EXPIRED"],"actions":[],
                    "finalState":{"orderId":"order-1002","status":"DELIVERED",
                    "refundedAmount":0,"currency":"KRW"},"residualRisks":[]}
                    """.formatted(taskId);
        }
        return new AgentModelResponse(
                AgentMessage.assistant(content, clock.instant()),
                List.of(),
                new AgentUsage(40, 120),
                "stop",
                Map.of("evaluation.mode", "scripted"));
    }

    private record ImmediateCall(String callId, AgentModelResponse response)
            implements AgentModelCall {

        @Override
        public AgentModelCallStatus poll() {
            return AgentModelCallStatus.READY;
        }

        @Override
        public AgentModelResponse result() {
            return response;
        }

        @Override
        public Throwable error() {
            return null;
        }

        @Override
        public void cancel() {
        }
    }
}

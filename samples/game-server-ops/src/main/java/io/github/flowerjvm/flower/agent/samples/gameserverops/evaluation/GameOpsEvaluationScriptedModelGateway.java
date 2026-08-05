package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.agent.gateway.AgentModelCall;
import io.github.flowerjvm.flower.agent.gateway.AgentModelCallStatus;
import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.agent.model.AgentMessage;
import io.github.flowerjvm.flower.agent.model.AgentModelRequest;
import io.github.flowerjvm.flower.agent.model.AgentModelResponse;
import io.github.flowerjvm.flower.agent.model.AgentRole;
import io.github.flowerjvm.flower.agent.model.AgentUsage;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.RestartServerTool;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic model used to exercise the real Agent loop without credentials. */
final class GameOpsEvaluationScriptedModelGateway implements AgentModelGateway {

    private final Clock clock;
    private final AtomicInteger callIds = new AtomicInteger();

    GameOpsEvaluationScriptedModelGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public AgentModelCall submit(AgentModelRequest request) {
        String serverId = serverId(request);
        long toolResults = request.messages().stream()
                .filter(message -> message.role() == AgentRole.TOOL)
                .count();
        AgentModelResponse response = "server-beta".equals(serverId)
                ? healthyResponse(request, toolResults)
                : degradedResponse(request, toolResults);
        return new ImmediateCall("evaluation-model-" + callIds.incrementAndGet(), response);
    }

    private AgentModelResponse healthyResponse(AgentModelRequest request, long toolResults) {
        if (toolResults == 0L) {
            return toolResponse(
                    "beta-status", "game.server.status", Map.of("serverId", "server-beta"));
        }
        if (toolResults == 1L) {
            return toolResponse(
                    "beta-logs",
                    "game.server.logs.search",
                    Map.of("serverId", "server-beta", "limit", 10));
        }
        String taskId = taskId(request);
        String content = """
                {"taskId":"%s","serverId":"server-beta","outcome":"NO_ACTION_NEEDED",
                "summary":"server-beta is healthy and recent logs show no incident.",
                "evidence":["Current state is HEALTHY","Health probe passed"],"actions":[],
                "finalState":{"serverId":"server-beta","state":"HEALTHY","restartCount":0},
                "residualRisks":[]}
                """.formatted(taskId);
        return finalResponse(content);
    }

    private AgentModelResponse degradedResponse(AgentModelRequest request, long toolResults) {
        if (toolResults == 0L) {
            return toolResponse(
                    "alpha-status", "game.server.status", Map.of("serverId", "server-alpha"));
        }
        if (toolResults == 1L) {
            return toolResponse(
                    "alpha-logs",
                    "game.server.logs.search",
                    Map.of("serverId", "server-alpha", "query", "ERROR", "limit", 10));
        }
        if (toolResults == 2L) {
            return toolResponse(
                    "alpha-restart",
                    "game.server.restart",
                    Map.of(
                            "serverId", "server-alpha",
                            "reason", "Repeated health probe and persistence failures"));
        }
        if (toolResults == 3L) {
            return toolResponse(
                    "alpha-verify", "game.server.status", Map.of("serverId", "server-alpha"));
        }
        String taskId = taskId(request);
        String content = """
                {"taskId":"%s","serverId":"server-alpha","outcome":"RESOLVED",
                "summary":"server-alpha recovered after one governed restart.",
                "evidence":["Health probe failed three times","Final state is HEALTHY"],
                "actions":[{"actionId":"game.server.restart","status":"SUCCEEDED",
                "reason":"Repeated health probe and persistence failures"}],
                "finalState":{"serverId":"server-alpha","state":"HEALTHY","restartCount":1},
                "residualRisks":["Database pool exhaustion may recur"]}
                """.formatted(taskId);
        return finalResponse(content);
    }

    private AgentModelResponse toolResponse(
            String callId,
            String toolName,
            Map<String, Object> arguments
    ) {
        ToolCall call = new ToolCall(callId, toolName, arguments);
        return new AgentModelResponse(
                AgentMessage.assistant("", List.of(call), clock.instant()),
                List.of(call),
                new AgentUsage(30, 8),
                "tool_calls",
                Map.of("evaluation.mode", "scripted"));
    }

    private AgentModelResponse finalResponse(String content) {
        return new AgentModelResponse(
                AgentMessage.assistant(content, clock.instant()),
                List.of(),
                new AgentUsage(40, 120),
                "stop",
                Map.of("evaluation.mode", "scripted"));
    }

    private static String serverId(AgentModelRequest request) {
        boolean beta = request.messages().stream()
                .map(AgentMessage::content)
                .filter(content -> content != null)
                .anyMatch(content -> content.contains("server-beta"));
        return beta ? "server-beta" : "server-alpha";
    }

    private static String taskId(AgentModelRequest request) {
        Object value = request.metadata().get(RestartServerTool.TASK_ID_METADATA);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("scripted evaluation request has no task id");
        }
        return String.valueOf(value);
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

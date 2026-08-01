package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

import io.github.flowerjvm.flower.agent.AgentSpec;
import io.github.flowerjvm.flower.agent.control.AgentBudget;
import io.github.flowerjvm.flower.agent.control.CompletionPolicy;
import io.github.flowerjvm.flower.agent.control.ModelTurnRetryPolicy;
import io.github.flowerjvm.flower.agent.flow.AgentRunFlow;
import io.github.flowerjvm.flower.agent.flow.AgentRunFlowFactory;
import io.github.flowerjvm.flower.agent.model.AgentMessage;
import io.github.flowerjvm.flower.agent.model.openaicompatible.OpenAiCompatibleAgentOptions;
import io.github.flowerjvm.flower.agent.run.AgentRun;
import io.github.flowerjvm.flower.agent.run.AgentRunStatus;
import io.github.flowerjvm.flower.agent.run.AgentThread;
import io.github.flowerjvm.flower.agent.samples.gameserverops.config.ModelProperties;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.RestartServerTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.workflow.GameOpsFlowSubmitter;
import io.github.flowerjvm.flower.agent.transcript.ContextBuilder;
import io.github.flowerjvm.flower.ai.harness.gateway.AiModelGateway;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCall;
import io.github.flowerjvm.flower.ai.harness.model.AiModelCallStatus;
import io.github.flowerjvm.flower.ai.harness.model.AiModelRequest;
import io.github.flowerjvm.flower.ai.harness.model.AiModelResponse;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FlowerAgentAiModelGateway implements AiModelGateway {

    public static final String TASK_ID_OPTION = "sample.taskId";

    private final AgentRunFlowFactory agentFactory;
    private final GameOpsFlowSubmitter flowSubmitter;
    private final GameOpsTaskRegistry taskRegistry;
    private final ModelProperties modelProperties;
    private final Clock clock;

    public FlowerAgentAiModelGateway(
            AgentRunFlowFactory agentFactory,
            GameOpsFlowSubmitter flowSubmitter,
            GameOpsTaskRegistry taskRegistry,
            ModelProperties modelProperties,
            Clock clock
    ) {
        this.agentFactory = agentFactory;
        this.flowSubmitter = flowSubmitter;
        this.taskRegistry = taskRegistry;
        this.modelProperties = modelProperties;
        this.clock = clock;
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        String taskId = request.options().get(TASK_ID_OPTION)
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("missing provider option: " + TASK_ID_OPTION));
        int attempt = taskRegistry.require(taskId).agentAttempts().size() + 1;
        Instant now = clock.instant();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(RestartServerTool.TASK_ID_METADATA, taskId);
        metadata.put(OpenAiCompatibleAgentOptions.TEMPERATURE, 0.1d);
        metadata.put(OpenAiCompatibleAgentOptions.MAX_COMPLETION_TOKENS, 1400);
        metadata.put(OpenAiCompatibleAgentOptions.PARALLEL_TOOL_CALLS, false);

        AgentSpec spec = new AgentSpec(
                "game-server-ops-agent",
                request.modelId().name(),
                systemPrompt(request.prompt()),
                new AgentBudget(10, 12, 50_000L, 8_000L, request.timeout()),
                modelProperties.timeout(),
                Duration.ofSeconds(30),
                ContextBuilder.fullTranscript(),
                CompletionPolicy.toolCallsThenText(),
                ModelTurnRetryPolicy.maxAttempts(2, Duration.ofSeconds(1)),
                metadata);
        AgentThread thread = new AgentThread(
                taskId + "-agent-attempt-" + attempt,
                now,
                Map.of(RestartServerTool.TASK_ID_METADATA, taskId));
        AgentRunFlow agentRun = agentFactory.createFlow(
                spec,
                thread,
                AgentMessage.user(userPrompt(request.prompt()), now));
        taskRegistry.attachAgent(taskId, agentRun);
        flowSubmitter.submit(agentRun.flow());
        return new AgentBackedModelCall(agentRun, request, now, clock);
    }

    private static String systemPrompt(RenderedPrompt prompt) {
        String joined = prompt.messages().stream()
                .filter(message -> message.role() == RenderedPrompt.Role.SYSTEM)
                .map(RenderedPrompt.Message::content)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return "Use tools for observable facts and governed operations. "
                + "Never invent tool results.\n\n" + joined;
    }

    private static String userPrompt(RenderedPrompt prompt) {
        StringBuilder builder = new StringBuilder();
        for (RenderedPrompt.Message message : prompt.messages()) {
            if (message.role() == RenderedPrompt.Role.SYSTEM) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append('[').append(message.role().name()).append("]\n").append(message.content());
        }
        if (builder.isEmpty()) {
            throw new IllegalArgumentException("agent-backed harness request requires a non-system message");
        }
        return builder.toString();
    }

    private static final class AgentBackedModelCall implements AiModelCall {

        private final AgentRunFlow agentRun;
        private final AiModelRequest request;
        private final Instant startedAt;
        private final Clock clock;

        private AgentBackedModelCall(
                AgentRunFlow agentRun,
                AiModelRequest request,
                Instant startedAt,
                Clock clock
        ) {
            this.agentRun = agentRun;
            this.request = request;
            this.startedAt = startedAt;
            this.clock = clock;
        }

        @Override
        public String callId() {
            return agentRun.run().runId();
        }

        @Override
        public AiModelCallStatus poll() {
            AgentRunStatus status = agentRun.run().status();
            if (!status.isTerminal()) {
                return AiModelCallStatus.PENDING;
            }
            return switch (status) {
                case COMPLETED -> AiModelCallStatus.READY;
                case CANCELLED -> AiModelCallStatus.CANCELLED;
                default -> AiModelCallStatus.FAILED;
            };
        }

        @Override
        public AiModelResponse result() {
            AgentRun run = agentRun.run();
            if (run.status() != AgentRunStatus.COMPLETED || run.finalMessage() == null) {
                throw new IllegalStateException("agent run is not completed: " + run.status());
            }
            AiModelResponse.ResponseMetadata metadata = new AiModelResponse.ResponseMetadata(
                    optionalInt(run.inputTokens()),
                    optionalInt(run.outputTokens()),
                    Optional.of(Duration.between(startedAt, clock.instant())),
                    Optional.of("agent-run-completed"),
                    Map.of("agentRunId", run.runId(), "threadId", run.threadId()));
            return new AiModelResponse(run.finalMessage().content(), request.modelId(), metadata);
        }

        @Override
        public Throwable error() {
            AgentRun run = agentRun.run();
            if (!run.status().isTerminal() || run.status() == AgentRunStatus.COMPLETED) {
                return null;
            }
            String code = run.failureCode() == null ? run.status().name() : run.failureCode();
            String message = run.failureMessage() == null || run.failureMessage().isBlank()
                    ? run.status().name()
                    : run.failureMessage();
            return new IllegalStateException(code + ": " + message);
        }

        @Override
        public void cancel() {
            agentRun.cancel("outer AI Harness call cancelled");
        }

        private static Optional<Integer> optionalInt(long value) {
            return value > Integer.MAX_VALUE ? Optional.of(Integer.MAX_VALUE) : Optional.of((int) value);
        }
    }
}

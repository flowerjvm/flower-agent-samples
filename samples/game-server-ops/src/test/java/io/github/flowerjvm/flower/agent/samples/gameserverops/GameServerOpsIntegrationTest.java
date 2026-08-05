package io.github.flowerjvm.flower.agent.samples.gameserverops;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.ActionExecutionStatus;
import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.action.InMemoryActionRegistry;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalGate;
import io.github.flowerjvm.flower.action.runtime.duplicate.InMemoryDuplicateActionPolicy;
import io.github.flowerjvm.flower.action.runtime.guard.PreExecutionDecision;
import io.github.flowerjvm.flower.action.runtime.run.InMemoryRunStore;
import io.github.flowerjvm.flower.action.runtime.validation.ValidationResult;
import io.github.flowerjvm.flower.agent.flow.AgentRunFlowFactory;
import io.github.flowerjvm.flower.agent.gateway.AgentModelCall;
import io.github.flowerjvm.flower.agent.gateway.AgentModelCallStatus;
import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.agent.model.AgentMessage;
import io.github.flowerjvm.flower.agent.model.AgentModelRequest;
import io.github.flowerjvm.flower.agent.model.AgentModelResponse;
import io.github.flowerjvm.flower.agent.model.AgentRole;
import io.github.flowerjvm.flower.agent.model.AgentUsage;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.GameServerOpsPolicyGate;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RestartGameServerAction;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RestartActionInputValidator;
import io.github.flowerjvm.flower.agent.samples.gameserverops.config.ModelProperties;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerState;
import io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation.GameOpsEvaluationOutputFactory;
import io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation.GameOpsEvaluationPlan;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.FlowerAgentAiModelGateway;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReport;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReportFindingExtractor;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReportPromptBuilder;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentTaskInput;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTask;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskService;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.RestartServerTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.SearchServerLogsTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.ServerStatusTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.workflow.GameOpsFlowSubmitter;
import io.github.flowerjvm.flower.agent.tool.InMemoryToolRegistry;
import io.github.flowerjvm.flower.agent.transcript.InMemoryTranscriptStore;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.validator.jackson.JacksonPojoSchemaValidator;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import io.github.flowerjvm.flower.evaluation.EvaluationCandidate;
import io.github.flowerjvm.flower.evaluation.EvaluationCaseStatus;
import io.github.flowerjvm.flower.evaluation.EvaluationDataset;
import io.github.flowerjvm.flower.evaluation.EvaluationExperiment;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationRunner;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GameServerOpsIntegrationTest {

    @Test
    void outerHarnessRetryDoesNotRestartTheServerTwice() throws Exception {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ManualClock flowerClock = new ManualClock(now.toEpochMilli());
        Worker flowerWorker = Worker.builder(GameOpsFlowSubmitter.WORKER_NAME).build();
        Engine engine = Engine.builder()
                .clock(flowerClock)
                .eventBus(InMemoryEventBus.create())
                .worker(flowerWorker)
                .build();
        engine.attach();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GameServerFleet fleet = new GameServerFleet(clock);
        RecordingAuditSink auditSink = new RecordingAuditSink();
        DefaultActionRuntime actionRuntime = actionRuntime(fleet, auditSink);
        GameOpsTaskRegistry taskRegistry = new GameOpsTaskRegistry();
        RestartServerTool restartTool = new RestartServerTool(
                actionRuntime,
                taskRegistry,
                objectMapper,
                Runnable::run,
                clock);
        AgentModelGateway scriptedModel = new ScriptedGameOpsModelGateway(clock);
        AgentRunFlowFactory agentFactory = new AgentRunFlowFactory(
                scriptedModel,
                new InMemoryToolRegistry(List.of(
                        new ServerStatusTool(fleet, objectMapper),
                        new SearchServerLogsTool(fleet, objectMapper),
                        restartTool)),
                new InMemoryTranscriptStore(),
                clock);
        GameOpsFlowSubmitter flowSubmitter = new GameOpsFlowSubmitter(engine);
        ModelProperties modelProperties = new ModelProperties(
                "http://localhost:11434/v1",
                "test-model",
                "",
                "",
                Duration.ofSeconds(10));
        FlowerAgentAiModelGateway agentProvider = new FlowerAgentAiModelGateway(
                agentFactory,
                flowSubmitter,
                taskRegistry,
                modelProperties,
                clock);
        AiHarnessSpec<IncidentTaskInput, IncidentReport> spec = AiHarnessSpec
                .<IncidentTaskInput, IncidentReport>builder()
                .harnessId("sample.game-server-ops")
                .defaultModelId(new ModelId("flower-agent", "test-model"))
                .defaultTimeout(Duration.ofMinutes(1))
                .promptVersion(new PromptVersion("game-server-ops", "test"))
                .promptBuilder(new IncidentReportPromptBuilder())
                .validator(new JacksonPojoSchemaValidator<>(IncidentReport.class, objectMapper))
                .refinePolicy(new MaxAttemptsRefinePolicy(2))
                .findingExtractor(new IncidentReportFindingExtractor())
                .findingSink((findings, context) -> {
                })
                .build();
        AiHarnessFlowFactory<IncidentTaskInput, IncidentReport> harnessFactory =
                new AiHarnessFlowFactory<>(agentProvider, spec, clock::instant);
        GameOpsTaskService service = new GameOpsTaskService(harnessFactory, flowSubmitter, taskRegistry, clock);

        GameOpsTask task = service.start(
                "Investigate server-alpha, restart it if justified, and report final state.");
        for (int tick = 0; tick < 250 && !task.harnessFlow().flow().state().isTerminal(); tick++) {
            flowerWorker.tickOnce();
            flowerClock.advance(1);
        }

        assertThat(task.harnessFlow().flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(task.harnessFlow().context().status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
        assertThat(task.harnessFlow().context().attempt()).isEqualTo(2);
        assertThat(task.agentAttempts()).hasSize(2);
        assertThat(task.actionAttempts()).hasSize(2);
        assertThat(task.actionAttempts()).allMatch(attempt -> attempt.status().name().equals("SUCCEEDED"));
        assertThat(fleet.server("server-alpha").state()).isEqualTo(GameServerState.HEALTHY);
        assertThat(fleet.server("server-alpha").restartCount()).isEqualTo(1);
        assertThat(auditSink.eventsForRuns(task.actionAttempts().stream().map(attempt -> attempt.runId()).toList()))
                .anyMatch(event -> event.type().name().equals("ACTION_DUPLICATE"));
        assertThat(task.harnessFlow().context().latestValidation())
                .hasValueSatisfying(result -> assertThat(result.isValid()).isTrue());

        EvaluationDataset evaluationDataset = EvaluationDataset
                .builder(GameOpsEvaluationPlan.DATASET_ID, GameOpsEvaluationPlan.DATASET_VERSION)
                .name("Degraded server integration scenario")
                .example(GameOpsEvaluationPlan.degradedServerRecovery())
                .build();
        EvaluationExperiment evaluation = EvaluationExperiment.builder("integration-evaluation")
                .dataset(evaluationDataset)
                .candidate(EvaluationCandidate.builder("game-ops-agent", "scripted-test").build())
                .target(example -> GameOpsEvaluationOutputFactory.from(
                        task, fleet.server("server-alpha"), Duration.ZERO))
                .suite(GameOpsEvaluationPlan.suite())
                .build();
        EvaluationExperimentResult evaluationResult = new EvaluationRunner(clock).run(evaluation);

        assertThat(evaluationResult.getCases()).singleElement()
                .extracting(value -> value.getStatus())
                .isEqualTo(EvaluationCaseStatus.PASS);
    }

    @Test
    void unauthorizedDuplicateCannotReadAnAuthorizedResult() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);
        GameServerFleet fleet = new GameServerFleet(clock);
        DefaultActionRuntime runtime = actionRuntime(fleet, new RecordingAuditSink());
        String taskId = "task-authorized";
        ActionProposal proposal = restartProposal(taskId, "server-alpha");

        var authorized = runtime.handle(proposal, actionContext(
                "authorized-run", taskId, "game-server-ops-agent", List.of("GAME_OPERATOR")));
        var deniedDuplicate = runtime.handle(proposal, actionContext(
                "denied-run", taskId, "intruder", List.of("GAME_OPERATOR")));

        assertThat(authorized.status()).isEqualTo(ActionExecutionStatus.SUCCEEDED);
        assertThat(deniedDuplicate.status()).isEqualTo(ActionExecutionStatus.DENIED);
        assertThat(deniedDuplicate.output()).isEmpty();
        assertThat(fleet.server("server-alpha").restartCount()).isEqualTo(1);
    }

    @Test
    void crossResourceDuplicateKeyIsRejectedBeforeResultLookup() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);
        GameServerFleet fleet = new GameServerFleet(clock);
        DefaultActionRuntime runtime = actionRuntime(fleet, new RecordingAuditSink());
        String taskId = "task-resource-scope";
        ActionProposal serverAlpha = restartProposal(taskId, "server-alpha");
        ActionProposal serverBetaWithAlphaKey = ActionProposal.builder(RestartGameServerAction.ACTION_ID)
                .requestChannel(ActionRequestChannel.COMMAND)
                .proposerType(ActionProposerType.AI_PLANNER)
                .requesterId("game-server-ops-agent")
                .reason("cross-resource duplicate test")
                .confidence(1.0d)
                .input(Map.of("serverId", "server-beta", "reason", "test"))
                .idempotencyKey(serverAlpha.idempotencyKey())
                .metadata(Map.of("taskId", taskId))
                .build();

        var original = runtime.handle(serverAlpha, actionContext(
                "alpha-run", taskId, "game-server-ops-agent", List.of("GAME_OPERATOR")));
        var crossResource = runtime.handle(serverBetaWithAlphaKey, actionContext(
                "beta-run", taskId, "game-server-ops-agent", List.of("GAME_OPERATOR")));

        assertThat(original.status()).isEqualTo(ActionExecutionStatus.SUCCEEDED);
        assertThat(crossResource.status()).isEqualTo(ActionExecutionStatus.VALIDATION_FAILED);
        assertThat(crossResource.output()).isEmpty();
        assertThat(fleet.server("server-beta").restartCount()).isZero();
    }

    private static DefaultActionRuntime actionRuntime(GameServerFleet fleet, RecordingAuditSink auditSink) {
        RestartGameServerAction restartAction = new RestartGameServerAction(fleet);
        ActionRegistry registry = new InMemoryActionRegistry(List.of(restartAction));
        return new DefaultActionRuntime(
                registry,
                new RestartActionInputValidator(fleet),
                new GameServerOpsPolicyGate(),
                ApprovalGate.unsupported(),
                new InMemoryDuplicateActionPolicy(),
                auditSink,
                null,
                new InMemoryRunStore(),
                (proposal, definition, context, policy) ->
                        fleet.server(String.valueOf(proposal.input().get("serverId"))).state()
                                == GameServerState.DEGRADED
                                ? PreExecutionDecision.allow()
                                : PreExecutionDecision.deny("SERVER_NOT_DEGRADED", "server is already healthy"));
    }

    private static ActionProposal restartProposal(String taskId, String serverId) {
        return ActionProposal.builder(RestartGameServerAction.ACTION_ID)
                .requestChannel(ActionRequestChannel.COMMAND)
                .proposerType(ActionProposerType.AI_PLANNER)
                .requesterId("game-server-ops-agent")
                .reason("health probe failures")
                .confidence(1.0d)
                .input(Map.of("serverId", serverId, "reason", "health probe failures"))
                .idempotencyKey(RestartGameServerAction.idempotencyKey(taskId, serverId))
                .metadata(Map.of("taskId", taskId))
                .build();
    }

    private static ExecutionContext actionContext(
            String runId,
            String taskId,
            String principal,
            List<String> roles
    ) {
        return new ExecutionContext(
                "sample",
                principal,
                runId,
                taskId,
                Map.of(
                        "actor.roles", roles,
                        "resource.type", "game-server",
                        "taskId", taskId));
    }

    private static final class ScriptedGameOpsModelGateway implements AgentModelGateway {

        private final Clock clock;
        private final AtomicInteger finalResponses = new AtomicInteger();

        private ScriptedGameOpsModelGateway(Clock clock) {
            this.clock = clock;
        }

        @Override
        public AgentModelCall submit(AgentModelRequest request) {
            long toolResults = request.messages().stream().filter(message -> message.role() == AgentRole.TOOL).count();
            AgentModelResponse response = switch ((int) toolResults) {
                case 0 -> toolResponse("status-call", "game.server.status", Map.of("serverId", "server-alpha"));
                case 1 -> toolResponse("logs-call", "game.server.logs.search",
                        Map.of("serverId", "server-alpha", "query", "ERROR", "limit", 10));
                case 2 -> toolResponse("restart-call", "game.server.restart",
                        Map.of("serverId", "server-alpha", "reason", "Repeated health probe failures"));
                case 3 -> toolResponse("final-status-call", "game.server.status",
                        Map.of("serverId", "server-alpha"));
                default -> finalResponse(request);
            };
            return new ImmediateAgentModelCall(response);
        }

        private AgentModelResponse toolResponse(String callId, String toolName, Map<String, Object> arguments) {
            ToolCall call = new ToolCall(callId, toolName, arguments);
            return new AgentModelResponse(
                    AgentMessage.assistant("", List.of(call), clock.instant()),
                    List.of(call),
                    new AgentUsage(30, 8),
                    "tool_calls",
                    Map.of());
        }

        private AgentModelResponse finalResponse(AgentModelRequest request) {
            String taskId = String.valueOf(request.metadata().get(RestartServerTool.TASK_ID_METADATA));
            String content = finalResponses.getAndIncrement() == 0
                    ? "not-json"
                    : """
                    {"taskId":"%s","serverId":"server-alpha","outcome":"RESOLVED",
                    "summary":"server-alpha recovered after one governed restart.",
                    "evidence":["Health probe failed three times","Final status is HEALTHY"],
                    "actions":[{"actionId":"game.server.restart","status":"SUCCEEDED",
                    "reason":"Repeated health probe failures"}],
                    "finalState":{"serverId":"server-alpha","state":"HEALTHY","restartCount":1},
                    "residualRisks":[]}
                    """.formatted(taskId);
            return new AgentModelResponse(
                    AgentMessage.assistant(content, clock.instant()),
                    List.of(),
                    new AgentUsage(40, 120),
                    "stop",
                    Map.of());
        }
    }

    private record ImmediateAgentModelCall(AgentModelResponse response) implements AgentModelCall {

        @Override
        public String callId() {
            return "scripted-call";
        }

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

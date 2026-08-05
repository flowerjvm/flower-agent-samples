package io.github.flowerjvm.flower.agent.samples.refundops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.action.InMemoryActionRegistry;
import io.github.flowerjvm.flower.action.runtime.audit.TraceSink;
import io.github.flowerjvm.flower.action.runtime.run.InMemoryRunStore;
import io.github.flowerjvm.flower.agent.gateway.AgentModelCall;
import io.github.flowerjvm.flower.agent.gateway.AgentModelCallStatus;
import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.agent.model.AgentMessage;
import io.github.flowerjvm.flower.agent.model.AgentModelRequest;
import io.github.flowerjvm.flower.agent.model.AgentModelResponse;
import io.github.flowerjvm.flower.agent.model.AgentRole;
import io.github.flowerjvm.flower.agent.model.AgentUsage;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.observation.AgentEventSink;
import io.github.flowerjvm.flower.agent.samples.refundops.action.IssueRefundAction;
import io.github.flowerjvm.flower.agent.samples.refundops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStatus;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.FlowerAgentAiModelGateway;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundOutcome;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReport;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReportFindingExtractor;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReportPromptBuilder;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundTaskInput;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTask;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskService;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.CheckRefundPolicyTool;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.GetOrderTool;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.IssueRefundTool;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.TraceCorrelationRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.workflow.RefundFlowSubmitter;
import io.github.flowerjvm.flower.agent.tool.InMemoryToolRegistry;
import io.github.flowerjvm.flower.agent.transcript.InMemoryTranscriptStore;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.observability.AiHarnessObservationTraceListener;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import io.github.flowerjvm.flower.ai.harness.validator.jackson.JacksonPojoSchemaValidator;
import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationEvent;
import io.github.flowerjvm.flower.observability.tracing.FlowerTraceSinkListener;
import io.github.flowerjvm.flower.observability.tracing.InMemoryFlowerObservationSink;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRefundOpsIntegrationTest {

    @Test
    void eligibleRefundIsRetriedOnceGovernedAndCorrelatedAcrossAllRuntimes() {
        Fixture fixture = new Fixture(true);

        RefundTask task = fixture.run(
                "order-1001",
                "Refund this delivered order if the current policy allows it.");

        assertThat(task.harnessFlow().flow().state()).isEqualTo(FlowState.FINISHED);
        assertThat(task.harnessFlow().context().status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
        assertThat(task.harnessFlow().context().attempt()).isEqualTo(2);
        assertThat(task.agentAttempts()).hasSize(2);
        assertThat(task.actionAttempts()).hasSize(2);
        assertThat(task.actionAttempts()).allMatch(attempt -> attempt.status().name().equals("SUCCEEDED"));
        assertThat(fixture.orders.order("order-1001").status()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(fixture.orders.refundExecutionCount()).isEqualTo(1);
        assertThat(fixture.audit.eventsForRuns(
                        task.actionAttempts().stream().map(attempt -> attempt.runId()).toList()))
                .anyMatch(event -> event.type().name().equals("ACTION_DUPLICATE"));

        ValidationResult<?> validation = task.harnessFlow().context().latestValidation().orElseThrow();
        assertThat(validation).isInstanceOf(ValidationResult.Valid.class);
        RefundReport report = (RefundReport) ((ValidationResult.Valid<?>) validation).value();
        assertThat(report.outcome()).isEqualTo(RefundOutcome.REFUNDED);
        assertThat(report.finalState().refundedAmount()).isEqualTo(54_000L);

        List<FlowerObservationEvent> trace = fixture.trace(task.taskId());
        assertThat(trace).isNotEmpty().allMatch(event -> event.traceId().equals(task.taskId()));
        assertThat(trace.stream().map(FlowerObservationEvent::source).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        "flower-core",
                        "flower-agent",
                        "flower-ai-harness",
                        "flower-action-runtime");
        assertThat(types(trace, "flower-core"))
                .contains("FLOW_STARTED", "STEP_COMPLETED", "FLOW_COMPLETED");
        assertThat(types(trace, "flower-agent"))
                .contains("MODEL_CALL_COMPLETED", "TOOL_CALL_COMPLETED", "RUN_COMPLETED");
        assertThat(types(trace, "flower-ai-harness"))
                .contains("VALIDATION_COMPLETED", "REFINE_TRIGGERED", "RUN_COMPLETED");
        assertThat(types(trace, "flower-action-runtime"))
                .contains("ACTION_EXECUTION_COMPLETED", "ACTION_DUPLICATE");

        String harnessRunId = task.harnessFlow().context().runId().value();
        assertThat(trace.stream()
                .filter(event -> event.source().equals("flower-agent"))
                .map(FlowerObservationEvent::parentRunId)
                .distinct())
                .containsExactly(harnessRunId);
        assertThat(trace.stream()
                .filter(event -> event.source().equals("flower-action-runtime"))
                .map(FlowerObservationEvent::parentRunId))
                .allMatch(parent -> task.agentAttempts().stream()
                        .anyMatch(attempt -> attempt.run().runId().equals(parent)));

        String storedAttributes = trace.stream()
                .map(event -> event.attributes().toString())
                .collect(Collectors.joining("\n"));
        assertThat(storedAttributes)
                .doesNotContain("customer-lee")
                .doesNotContain("Refund this delivered order")
                .doesNotContain("Delivered within the automatic refund window")
                .doesNotContain("54000");
    }

    @Test
    void expiredRefundWindowFinishesWithoutAnAction() {
        Fixture fixture = new Fixture(false);

        RefundTask task = fixture.run("order-1002", "Please refund this order if it is eligible.");

        assertThat(task.harnessFlow().context().status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
        assertThat(task.actionAttempts()).isEmpty();
        assertThat(fixture.orders.order("order-1002").status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(fixture.orders.refundExecutionCount()).isZero();
        assertThat(fixture.trace(task.taskId()).stream().map(FlowerObservationEvent::source))
                .contains("flower-core", "flower-agent", "flower-ai-harness")
                .doesNotContain("flower-action-runtime");
    }

    @Test
    void highValueRefundIsRoutedToManualReviewWithoutAnAction() {
        Fixture fixture = new Fixture(false);

        RefundTask task = fixture.run("order-1003", "Refund this high-value order.");

        assertThat(task.harnessFlow().context().status()).isEqualTo(AiHarnessRunStatus.SUCCEEDED);
        assertThat(task.actionAttempts()).isEmpty();
        assertThat(fixture.orders.refundExecutionCount()).isZero();
        assertThat(task.harnessFlow().context().latestFindings())
                .anyMatch(finding -> finding.code().equals("MANUAL_REVIEW_REQUIRED"));
    }

    private static Set<String> types(List<FlowerObservationEvent> events, String source) {
        return events.stream()
                .filter(event -> source.equals(event.source()))
                .map(FlowerObservationEvent::eventType)
                .collect(Collectors.toSet());
    }

    private static final class Fixture {

        private final Instant now = Instant.parse("2026-08-05T00:00:00Z");
        private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        private final ManualClock flowerClock = new ManualClock(now.toEpochMilli());
        private final Worker worker = Worker.builder(RefundFlowSubmitter.WORKER_NAME).build();
        private final TraceCorrelationRegistry correlations;
        private final InMemoryFlowerObservationSink observations;
        private final OrderStore orders;
        private final RecordingAuditSink audit;
        private final RefundTaskService service;

        private Fixture(boolean invalidFirstResponse) {
            ObservationConfiguration observationConfiguration = new ObservationConfiguration();
            correlations = observationConfiguration.traceCorrelationRegistry();
            observations = observationConfiguration.flowerObservationStore();
            FlowerTraceSinkListener coreTrace = observationConfiguration.flowerTraceListener(
                    observations, correlations);
            AgentEventSink agentEvents = observationConfiguration.agentEventSink(observations, correlations);
            AiHarnessObservationTraceListener harnessTrace =
                    observationConfiguration.aiHarnessObservationTraceListener(
                            observations, correlations, clock);
            TraceSink actionTrace = observationConfiguration.refundActionTraceSink(
                    observations, correlations);

            Engine engine = Engine.builder()
                    .clock(flowerClock)
                    .eventBus(InMemoryEventBus.create())
                    .listener(coreTrace)
                    .worker(worker)
                    .build();
            engine.attach();

            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            orders = new OrderStore(clock);
            IssueRefundAction refundAction = new IssueRefundAction(orders);
            ActionRegistry actionRegistry = new InMemoryActionRegistry(List.of(refundAction));
            audit = new RecordingAuditSink();
            DomainActionConfiguration actionConfiguration = new DomainActionConfiguration();
            DefaultActionRuntime actionRuntime = actionConfiguration.refundActionRuntime(
                    actionRegistry,
                    audit,
                    new InMemoryRunStore(),
                    actionTrace,
                    orders);

            RefundTaskRegistry tasks = new RefundTaskRegistry();
            IssueRefundTool refundTool = new IssueRefundTool(
                    actionRuntime, tasks, correlations, objectMapper, Runnable::run, clock);
            var tools = new InMemoryToolRegistry(List.of(
                    new GetOrderTool(orders, objectMapper),
                    new CheckRefundPolicyTool(orders, objectMapper),
                    refundTool));
            RefundFlowSubmitter submitter = new RefundFlowSubmitter(engine);
            ModelProperties properties = new ModelProperties(
                    "http://localhost:11434/v1",
                    "scripted-refund-model",
                    "",
                    "",
                    Duration.ofSeconds(10));
            FlowerAgentAiModelGateway agentProvider = new FlowerAgentAiModelGateway(
                    new ScriptedRefundModelGateway(clock, invalidFirstResponse),
                    tools,
                    new InMemoryTranscriptStore(),
                    agentEvents,
                    submitter,
                    tasks,
                    correlations,
                    properties,
                    clock);
            AiHarnessSpec<RefundTaskInput, RefundReport> spec = AiHarnessSpec
                    .<RefundTaskInput, RefundReport>builder()
                    .harnessId("sample.customer-refund-ops")
                    .defaultModelId(new ModelId("flower-agent", "scripted-refund-model"))
                    .defaultTimeout(Duration.ofMinutes(1))
                    .promptVersion(new PromptVersion("customer-refund-ops", "test"))
                    .promptBuilder(new RefundReportPromptBuilder())
                    .validator(new JacksonPojoSchemaValidator<>(RefundReport.class, objectMapper))
                    .refinePolicy(new MaxAttemptsRefinePolicy(2))
                    .findingExtractor(new RefundReportFindingExtractor())
                    .findingSink((findings, context) -> {
                    })
                    .addTraceListener(harnessTrace)
                    .build();
            AiHarnessFlowFactory<RefundTaskInput, RefundReport> harnessFactory =
                    new AiHarnessFlowFactory<>(agentProvider, spec, clock::instant);
            service = new RefundTaskService(
                    harnessFactory, submitter, tasks, correlations, orders, clock);
        }

        private RefundTask run(String orderId, String request) {
            RefundTask task = service.start(orderId, request);
            for (int tick = 0; tick < 400 && !task.harnessFlow().flow().state().isTerminal(); tick++) {
                worker.tickOnce();
                flowerClock.advance(1L);
            }
            assertThat(task.harnessFlow().flow().state().isTerminal()).isTrue();
            return task;
        }

        private List<FlowerObservationEvent> trace(String traceId) {
            return observations.snapshot().stream()
                    .filter(event -> traceId.equals(event.traceId()))
                    .toList();
        }
    }

    private static final class ScriptedRefundModelGateway implements AgentModelGateway {

        private final Clock clock;
        private final boolean invalidFirstResponse;
        private final AtomicInteger finalResponses = new AtomicInteger();
        private final AtomicInteger callIds = new AtomicInteger();

        private ScriptedRefundModelGateway(Clock clock, boolean invalidFirstResponse) {
            this.clock = clock;
            this.invalidFirstResponse = invalidFirstResponse;
        }

        @Override
        public AgentModelCall submit(AgentModelRequest request) {
            String orderId = String.valueOf(request.metadata().get("sample.orderId"));
            long toolResults = request.messages().stream()
                    .filter(message -> message.role() == AgentRole.TOOL)
                    .count();
            AgentModelResponse response;
            if (toolResults == 0L) {
                response = toolResponse("order", GetOrderTool.TOOL_NAME, Map.of("orderId", orderId));
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
                response = toolResponse("verify", GetOrderTool.TOOL_NAME, Map.of("orderId", orderId));
            } else {
                response = finalResponse(request, orderId);
            }
            return new ImmediateAgentModelCall("scripted-" + callIds.incrementAndGet(), response);
        }

        private AgentModelResponse toolResponse(
                String idPrefix,
                String toolName,
                Map<String, Object> arguments
        ) {
            ToolCall call = new ToolCall(idPrefix + "-call", toolName, arguments);
            return new AgentModelResponse(
                    AgentMessage.assistant("", List.of(call), clock.instant()),
                    List.of(call),
                    new AgentUsage(30, 8),
                    "tool_calls",
                    Map.of());
        }

        private AgentModelResponse finalResponse(AgentModelRequest request, String orderId) {
            String taskId = String.valueOf(request.metadata().get(IssueRefundTool.TASK_ID_METADATA));
            int responseNumber = finalResponses.getAndIncrement();
            String content;
            if (invalidFirstResponse && responseNumber == 0) {
                content = "not-json";
            } else if ("order-1001".equals(orderId)) {
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
                        "summary":"The refund window has expired, so no automatic action was taken.",
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
                    Map.of());
        }
    }

    private record ImmediateAgentModelCall(
            String callId,
            AgentModelResponse response
    ) implements AgentModelCall {

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

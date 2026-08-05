package io.github.flowerjvm.flower.agent.samples.refundops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.agent.model.openaicompatible.OpenAiCompatibleAgentGatewayConfig;
import io.github.flowerjvm.flower.agent.model.openaicompatible.OpenAiCompatibleAgentModelGateway;
import io.github.flowerjvm.flower.agent.observation.AgentEventSink;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.FlowerAgentAiModelGateway;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReport;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReportFindingExtractor;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReportPromptBuilder;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundTaskInput;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskService;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.CheckRefundPolicyTool;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.GetOrderTool;
import io.github.flowerjvm.flower.agent.samples.refundops.tool.IssueRefundTool;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.TraceCorrelationRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.workflow.RefundFlowSubmitter;
import io.github.flowerjvm.flower.agent.tool.InMemoryToolRegistry;
import io.github.flowerjvm.flower.agent.tool.ToolRegistry;
import io.github.flowerjvm.flower.agent.transcript.InMemoryTranscriptStore;
import io.github.flowerjvm.flower.agent.transcript.TranscriptStore;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
import io.github.flowerjvm.flower.ai.harness.observability.AiHarnessObservationTraceListener;
import io.github.flowerjvm.flower.ai.harness.prompt.PromptVersion;
import io.github.flowerjvm.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.flowerjvm.flower.ai.harness.spec.AiHarnessSpec;
import io.github.flowerjvm.flower.ai.harness.spi.AiHarnessClock;
import io.github.flowerjvm.flower.ai.harness.validator.jackson.JacksonPojoSchemaValidator;
import io.github.flowerjvm.flower.core.engine.Engine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AgentHarnessConfiguration {

    @Bean
    RefundTaskRegistry refundTaskRegistry() {
        return new RefundTaskRegistry();
    }

    @Bean
    RefundFlowSubmitter refundFlowSubmitter(Engine engine) {
        return new RefundFlowSubmitter(engine);
    }

    @Bean(name = "refundToolExecutor", destroyMethod = "shutdown")
    ExecutorService refundToolExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                runnable -> {
                    Thread thread = new Thread(runnable, "refund-tool-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "sample.evaluation",
            name = "scripted-model",
            havingValue = "false",
            matchIfMissing = true)
    AgentModelGateway refundAgentModelGateway(ModelProperties properties) {
        OpenAiCompatibleAgentGatewayConfig config = OpenAiCompatibleAgentGatewayConfig
                .builder(properties.baseUrl())
                .apiKey(ModelCredentialResolver.resolve(properties))
                .build();
        return new OpenAiCompatibleAgentModelGateway(config);
    }

    @Bean
    GetOrderTool getOrderTool(OrderStore orders, ObjectMapper objectMapper) {
        return new GetOrderTool(orders, objectMapper);
    }

    @Bean
    CheckRefundPolicyTool checkRefundPolicyTool(OrderStore orders, ObjectMapper objectMapper) {
        return new CheckRefundPolicyTool(orders, objectMapper);
    }

    @Bean
    IssueRefundTool issueRefundTool(
            DefaultActionRuntime refundActionRuntime,
            RefundTaskRegistry tasks,
            TraceCorrelationRegistry correlations,
            ObjectMapper objectMapper,
            @Qualifier("refundToolExecutor") ExecutorService executor,
            Clock sampleClock
    ) {
        return new IssueRefundTool(
                refundActionRuntime, tasks, correlations, objectMapper, executor, sampleClock);
    }

    @Bean
    ToolRegistry refundToolRegistry(
            GetOrderTool orderTool,
            CheckRefundPolicyTool policyTool,
            IssueRefundTool refundTool
    ) {
        return new InMemoryToolRegistry(List.of(orderTool, policyTool, refundTool));
    }

    @Bean
    TranscriptStore refundTranscriptStore() {
        return new InMemoryTranscriptStore();
    }

    @Bean
    FlowerAgentAiModelGateway flowerAgentAiModelGateway(
            AgentModelGateway refundAgentModelGateway,
            ToolRegistry refundToolRegistry,
            TranscriptStore refundTranscriptStore,
            AgentEventSink agentEventSink,
            RefundFlowSubmitter flowSubmitter,
            RefundTaskRegistry tasks,
            TraceCorrelationRegistry correlations,
            ModelProperties properties,
            Clock sampleClock
    ) {
        return new FlowerAgentAiModelGateway(
                refundAgentModelGateway,
                refundToolRegistry,
                refundTranscriptStore,
                agentEventSink,
                flowSubmitter,
                tasks,
                correlations,
                properties,
                sampleClock);
    }

    @Bean
    AiHarnessClock refundHarnessClock(Clock sampleClock) {
        return sampleClock::instant;
    }

    @Bean
    AiHarnessSpec<RefundTaskInput, RefundReport> refundHarnessSpec(
            ModelProperties properties,
            ObjectMapper objectMapper,
            AiHarnessObservationTraceListener observationListener
    ) {
        return AiHarnessSpec.<RefundTaskInput, RefundReport>builder()
                .harnessId("sample.customer-refund-ops")
                .defaultModelId(new ModelId("flower-agent", properties.model()))
                .defaultTimeout(Duration.ofMinutes(3))
                .promptVersion(new PromptVersion("customer-refund-ops", "1.0.0"))
                .promptBuilder(new RefundReportPromptBuilder())
                .validator(new JacksonPojoSchemaValidator<>(RefundReport.class, objectMapper))
                .refinePolicy(new MaxAttemptsRefinePolicy(2))
                .findingExtractor(new RefundReportFindingExtractor())
                .findingSink((findings, context) -> {
                })
                .addTraceListener(observationListener)
                .build();
    }

    @Bean
    AiHarnessFlowFactory<RefundTaskInput, RefundReport> refundHarnessFlowFactory(
            FlowerAgentAiModelGateway gateway,
            AiHarnessSpec<RefundTaskInput, RefundReport> spec,
            AiHarnessClock refundHarnessClock
    ) {
        return new AiHarnessFlowFactory<>(gateway, spec, refundHarnessClock);
    }

    @Bean
    RefundTaskService refundTaskService(
            AiHarnessFlowFactory<RefundTaskInput, RefundReport> harnessFactory,
            RefundFlowSubmitter flowSubmitter,
            RefundTaskRegistry tasks,
            TraceCorrelationRegistry correlations,
            OrderStore orders,
            Clock sampleClock
    ) {
        return new RefundTaskService(
                harnessFactory, flowSubmitter, tasks, correlations, orders, sampleClock);
    }
}

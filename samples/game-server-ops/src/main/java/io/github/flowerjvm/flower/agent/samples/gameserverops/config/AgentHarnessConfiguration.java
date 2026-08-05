package io.github.flowerjvm.flower.agent.samples.gameserverops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.agent.flow.AgentRunFlowFactory;
import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.agent.model.openaicompatible.OpenAiCompatibleAgentGatewayConfig;
import io.github.flowerjvm.flower.agent.model.openaicompatible.OpenAiCompatibleAgentModelGateway;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.FlowerAgentAiModelGateway;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReport;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReportFindingExtractor;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReportPromptBuilder;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentTaskInput;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskService;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.RestartServerTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.SearchServerLogsTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.tool.ServerStatusTool;
import io.github.flowerjvm.flower.agent.samples.gameserverops.workflow.GameOpsFlowSubmitter;
import io.github.flowerjvm.flower.agent.tool.InMemoryToolRegistry;
import io.github.flowerjvm.flower.agent.tool.ToolRegistry;
import io.github.flowerjvm.flower.agent.transcript.InMemoryTranscriptStore;
import io.github.flowerjvm.flower.agent.transcript.TranscriptStore;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ModelId;
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
    GameOpsTaskRegistry gameOpsTaskRegistry() {
        return new GameOpsTaskRegistry();
    }

    @Bean
    GameOpsFlowSubmitter gameOpsFlowSubmitter(Engine engine) {
        return new GameOpsFlowSubmitter(engine);
    }

    @Bean(name = "agentToolExecutor", destroyMethod = "shutdown")
    ExecutorService agentToolExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                runnable -> {
                    Thread thread = new Thread(runnable, "game-ops-tool-" + threadNumber.incrementAndGet());
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
    AgentModelGateway agentModelGateway(ModelProperties properties) {
        String apiKey = ModelCredentialResolver.resolve(properties);
        OpenAiCompatibleAgentGatewayConfig config = OpenAiCompatibleAgentGatewayConfig
                .builder(properties.baseUrl())
                .apiKey(apiKey)
                .build();
        return new OpenAiCompatibleAgentModelGateway(config);
    }

    @Bean
    ServerStatusTool serverStatusTool(GameServerFleet fleet, ObjectMapper objectMapper) {
        return new ServerStatusTool(fleet, objectMapper);
    }

    @Bean
    SearchServerLogsTool searchServerLogsTool(GameServerFleet fleet, ObjectMapper objectMapper) {
        return new SearchServerLogsTool(fleet, objectMapper);
    }

    @Bean
    RestartServerTool restartServerTool(
            DefaultActionRuntime gameServerActionRuntime,
            GameOpsTaskRegistry taskRegistry,
            ObjectMapper objectMapper,
            @Qualifier("agentToolExecutor") ExecutorService executor,
            Clock sampleClock
    ) {
        return new RestartServerTool(
                gameServerActionRuntime,
                taskRegistry,
                objectMapper,
                executor,
                sampleClock);
    }

    @Bean
    ToolRegistry agentToolRegistry(
            ServerStatusTool statusTool,
            SearchServerLogsTool logsTool,
            RestartServerTool restartTool
    ) {
        return new InMemoryToolRegistry(List.of(statusTool, logsTool, restartTool));
    }

    @Bean
    TranscriptStore agentTranscriptStore() {
        return new InMemoryTranscriptStore();
    }

    @Bean
    AgentRunFlowFactory agentRunFlowFactory(
            AgentModelGateway agentModelGateway,
            ToolRegistry agentToolRegistry,
            TranscriptStore agentTranscriptStore,
            Clock sampleClock
    ) {
        return new AgentRunFlowFactory(
                agentModelGateway,
                agentToolRegistry,
                agentTranscriptStore,
                sampleClock);
    }

    @Bean
    FlowerAgentAiModelGateway flowerAgentAiModelGateway(
            AgentRunFlowFactory agentRunFlowFactory,
            GameOpsFlowSubmitter flowSubmitter,
            GameOpsTaskRegistry taskRegistry,
            ModelProperties properties,
            Clock sampleClock
    ) {
        return new FlowerAgentAiModelGateway(
                agentRunFlowFactory,
                flowSubmitter,
                taskRegistry,
                properties,
                sampleClock);
    }

    @Bean
    AiHarnessClock aiHarnessClock(Clock sampleClock) {
        return sampleClock::instant;
    }

    @Bean
    AiHarnessSpec<IncidentTaskInput, IncidentReport> incidentHarnessSpec(
            ModelProperties properties,
            ObjectMapper objectMapper
    ) {
        return AiHarnessSpec.<IncidentTaskInput, IncidentReport>builder()
                .harnessId("sample.game-server-ops")
                .defaultModelId(new ModelId("flower-agent", properties.model()))
                .defaultTimeout(Duration.ofMinutes(3))
                .promptVersion(new PromptVersion("game-server-ops", "1.0.0"))
                .promptBuilder(new IncidentReportPromptBuilder())
                .validator(new JacksonPojoSchemaValidator<>(IncidentReport.class, objectMapper))
                .refinePolicy(new MaxAttemptsRefinePolicy(2))
                .findingExtractor(new IncidentReportFindingExtractor())
                .findingSink((findings, context) -> {
                })
                .build();
    }

    @Bean
    AiHarnessFlowFactory<IncidentTaskInput, IncidentReport> incidentHarnessFlowFactory(
            FlowerAgentAiModelGateway flowerAgentAiModelGateway,
            AiHarnessSpec<IncidentTaskInput, IncidentReport> incidentHarnessSpec,
            AiHarnessClock aiHarnessClock
    ) {
        return new AiHarnessFlowFactory<>(flowerAgentAiModelGateway, incidentHarnessSpec, aiHarnessClock);
    }

    @Bean
    GameOpsTaskService gameOpsTaskService(
            AiHarnessFlowFactory<IncidentTaskInput, IncidentReport> incidentHarnessFlowFactory,
            GameOpsFlowSubmitter flowSubmitter,
            GameOpsTaskRegistry registry,
            Clock sampleClock
    ) {
        return new GameOpsTaskService(incidentHarnessFlowFactory, flowSubmitter, registry, sampleClock);
    }
}

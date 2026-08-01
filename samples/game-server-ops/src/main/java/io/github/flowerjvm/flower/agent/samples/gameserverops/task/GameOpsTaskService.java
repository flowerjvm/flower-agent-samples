package io.github.flowerjvm.flower.agent.samples.gameserverops.task;

import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.FlowerAgentAiModelGateway;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.GameOpsHarnessAttributes;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReport;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentTaskInput;
import io.github.flowerjvm.flower.agent.samples.gameserverops.workflow.GameOpsFlowSubmitter;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;

import java.time.Clock;
import java.util.UUID;

public final class GameOpsTaskService {

    private final AiHarnessFlowFactory<IncidentTaskInput, IncidentReport> harnessFactory;
    private final GameOpsFlowSubmitter flowSubmitter;
    private final GameOpsTaskRegistry registry;
    private final Clock clock;

    public GameOpsTaskService(
            AiHarnessFlowFactory<IncidentTaskInput, IncidentReport> harnessFactory,
            GameOpsFlowSubmitter flowSubmitter,
            GameOpsTaskRegistry registry,
            Clock clock
    ) {
        this.harnessFactory = harnessFactory;
        this.flowSubmitter = flowSubmitter;
        this.registry = registry;
        this.clock = clock;
    }

    public GameOpsTask start(String request) {
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("request must not be blank");
        }
        String taskId = UUID.randomUUID().toString();
        GameOpsTask task = registry.create(taskId, request.trim(), clock.instant());
        ProviderOptions options = ProviderOptions.empty()
                .with(FlowerAgentAiModelGateway.TASK_ID_OPTION, taskId);
        AiHarnessFlow harnessFlow = harnessFactory.createFlow(
                new IncidentTaskInput(taskId, request),
                AiHarnessFlowFactory.RunOverrides.builder()
                        .providerOptions(options)
                        .cancellationToken(task.cancellationToken())
                        .attribute(GameOpsHarnessAttributes.TASK_ID, taskId)
                        .build());
        registry.attachHarness(taskId, harnessFlow);
        flowSubmitter.submit(harnessFlow.flow());
        return task;
    }

    public void cancel(String taskId) {
        GameOpsTask task = registry.require(taskId);
        task.cancellationToken().cancel("operator cancelled task");
        task.agentAttempts().forEach(run -> run.cancel("operator cancelled task"));
    }
}

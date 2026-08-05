package io.github.flowerjvm.flower.agent.samples.refundops.task;

import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.FlowerAgentAiModelGateway;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReport;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundTaskInput;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.TraceCorrelationRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.workflow.RefundFlowSubmitter;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.flowerjvm.flower.ai.harness.model.ProviderOptions;

import java.time.Clock;
import java.util.UUID;

public final class RefundTaskService {

    private final AiHarnessFlowFactory<RefundTaskInput, RefundReport> harnessFactory;
    private final RefundFlowSubmitter flowSubmitter;
    private final RefundTaskRegistry tasks;
    private final TraceCorrelationRegistry correlations;
    private final OrderStore orders;
    private final Clock clock;

    public RefundTaskService(
            AiHarnessFlowFactory<RefundTaskInput, RefundReport> harnessFactory,
            RefundFlowSubmitter flowSubmitter,
            RefundTaskRegistry tasks,
            TraceCorrelationRegistry correlations,
            OrderStore orders,
            Clock clock
    ) {
        this.harnessFactory = harnessFactory;
        this.flowSubmitter = flowSubmitter;
        this.tasks = tasks;
        this.correlations = correlations;
        this.orders = orders;
        this.clock = clock;
    }

    public RefundTask start(String orderId, String request) {
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("request must not be blank");
        }
        orders.order(orderId);
        String taskId = UUID.randomUUID().toString();
        RefundTask task = tasks.create(taskId, orderId.trim(), request.trim(), clock.instant());
        ProviderOptions options = ProviderOptions.empty()
                .with(FlowerAgentAiModelGateway.TASK_ID_OPTION, taskId);
        AiHarnessFlow harness = harnessFactory.createFlow(
                new RefundTaskInput(taskId, task.orderId(), task.request()),
                AiHarnessFlowFactory.RunOverrides.builder()
                        .providerOptions(options)
                        .cancellationToken(task.cancellationToken())
                        .build());
        tasks.attachHarness(taskId, harness);
        correlations.register(harness.context().runId().value(), taskId, null);
        flowSubmitter.submit(harness.flow());
        return task;
    }

    public void cancel(String taskId) {
        RefundTask task = tasks.require(taskId);
        task.cancellationToken().cancel("operator cancelled task");
        task.agentAttempts().forEach(run -> run.cancel("operator cancelled task"));
    }
}

package io.github.flowerjvm.flower.agent.samples.refundops.api;

import io.github.flowerjvm.flower.action.runtime.audit.AuditEvent;
import io.github.flowerjvm.flower.agent.run.AgentRun;
import io.github.flowerjvm.flower.agent.samples.refundops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderSnapshot;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReport;
import io.github.flowerjvm.flower.agent.samples.refundops.task.ActionAttemptView;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTask;
import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import io.github.flowerjvm.flower.observability.tracing.InMemoryFlowerObservationSink;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RefundTaskView(
        String taskId,
        String orderId,
        String request,
        Instant createdAt,
        String status,
        int harnessAttempts,
        String terminalReason,
        List<AgentAttemptView> agentAttempts,
        List<ActionAttemptView> actions,
        List<AuditView> audit,
        List<AiFinding> findings,
        OrderSnapshot currentOrder,
        RefundReport report,
        Map<String, Long> traceEventsBySource
) {

    public static RefundTaskView from(
            RefundTask task,
            OrderStore orders,
            RecordingAuditSink auditSink,
            InMemoryFlowerObservationSink observations
    ) {
        AiHarnessFlow harness = task.harnessFlow();
        List<String> actionRunIds = task.actionAttempts().stream().map(ActionAttemptView::runId).toList();
        Map<String, Long> sourceCounts = observations.snapshot().stream()
                .filter(event -> task.taskId().equals(event.traceId()))
                .collect(Collectors.groupingBy(event -> event.source(), Collectors.counting()));
        return new RefundTaskView(
                task.taskId(),
                task.orderId(),
                task.request(),
                task.createdAt(),
                harness == null ? "QUEUED" : harness.context().status().name(),
                harness == null ? 0 : harness.context().attempt(),
                harness == null ? "" : harness.context().terminalReason().orElse(""),
                task.agentAttempts().stream().map(AgentAttemptView::from).toList(),
                task.actionAttempts(),
                auditSink.eventsForRuns(actionRunIds).stream().map(AuditView::from).toList(),
                harness == null ? List.of() : harness.context().latestFindings(),
                orders.order(task.orderId()),
                harness == null ? null : validatedReport(harness),
                Map.copyOf(sourceCounts));
    }

    private static RefundReport validatedReport(AiHarnessFlow harness) {
        ValidationResult<?> validation = harness.context().latestValidation().orElse(null);
        if (validation instanceof ValidationResult.Valid<?> valid && valid.value() instanceof RefundReport report) {
            return report;
        }
        return null;
    }

    public record AgentAttemptView(
            String runId,
            String status,
            int turns,
            int toolCalls,
            long inputTokens,
            long outputTokens,
            String failureCode
    ) {
        static AgentAttemptView from(io.github.flowerjvm.flower.agent.flow.AgentRunFlow flow) {
            AgentRun run = flow.run();
            return new AgentAttemptView(
                    run.runId(),
                    run.status().name(),
                    run.turnCount(),
                    run.toolCalls(),
                    run.inputTokens(),
                    run.outputTokens(),
                    run.failureCode() == null ? "" : run.failureCode());
        }
    }

    public record AuditView(
            String type,
            String runId,
            String actionId,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        static AuditView from(AuditEvent event) {
            return new AuditView(
                    event.type().name(),
                    event.runId(),
                    event.actionId(),
                    event.occurredAt(),
                    event.payload());
        }
    }
}

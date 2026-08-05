package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionStatus;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderSnapshot;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStatus;
import io.github.flowerjvm.flower.agent.samples.refundops.harness.RefundReport;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTask;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import io.github.flowerjvm.flower.evaluation.EvaluationMetrics;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;

import java.time.Duration;

/** Maps a completed task and durable domain facts into evaluation output. */
public final class RefundOpsEvaluationOutputFactory {

    private RefundOpsEvaluationOutputFactory() {
    }

    public static EvaluationOutput from(
            RefundTask task,
            OrderSnapshot order,
            int refundExecutionCount,
            Duration elapsed
    ) {
        if (task == null || order == null || elapsed == null) {
            throw new IllegalArgumentException("task, order, and elapsed must not be null");
        }
        RefundReport report = validatedReport(task);
        long turns = task.agentAttempts().stream().mapToLong(run -> run.run().turnCount()).sum();
        long toolCalls = task.agentAttempts().stream().mapToLong(run -> run.run().toolCalls()).sum();
        long inputTokens = task.agentAttempts().stream().mapToLong(run -> run.run().inputTokens()).sum();
        long outputTokens = task.agentAttempts().stream().mapToLong(run -> run.run().outputTokens()).sum();
        boolean allActionsSucceeded = task.actionAttempts().stream()
                .allMatch(action -> action.status() == ActionExecutionStatus.SUCCEEDED);
        long domainRefundedAmount = order.status() == OrderStatus.REFUNDED
                ? order.paidAmount() : 0L;
        boolean reportMatchesDomain = report.orderId().equals(order.orderId())
                && report.finalState().orderId().equals(order.orderId())
                && report.finalState().status() == order.status()
                && report.finalState().refundedAmount() == domainRefundedAmount
                && report.finalState().currency().equals(order.currency());
        boolean manualReviewRequired = task.harnessFlow().context().latestFindings().stream()
                .anyMatch(finding -> "MANUAL_REVIEW_REQUIRED".equals(finding.code()));

        return EvaluationOutput.builder()
                .actual("orderId", order.orderId())
                .actual("outcome", report.outcome().name())
                .actual("reportedStatus", report.finalState().status().name())
                .actual("domainStatus", order.status().name())
                .actual("refundExecutionCount", refundExecutionCount)
                .actual("actionObserved", !task.actionAttempts().isEmpty())
                .actual("actionAttemptCount", task.actionAttempts().size())
                .actual("allActionAttemptsSucceeded", allActionsSucceeded)
                .actual("manualReviewRequired", manualReviewRequired)
                .actual("reportMatchesDomain", reportMatchesDomain)
                .actual("evidence", report.evidence())
                .metric(EvaluationMetrics.DURATION_MILLIS, Math.max(0L, elapsed.toMillis()))
                .metric(EvaluationMetrics.INPUT_TOKENS, inputTokens)
                .metric(EvaluationMetrics.OUTPUT_TOKENS, outputTokens)
                .metric(EvaluationMetrics.TOOL_CALLS, toolCalls)
                .metric(EvaluationMetrics.MODEL_CALLS, turns)
                .metric(EvaluationMetrics.TURNS, turns)
                .metric("harnessAttempts", task.harnessFlow().context().attempt())
                .metric("actionAttempts", task.actionAttempts().size())
                .traceId(task.taskId())
                .runId(task.harnessFlow().context().runId().value())
                .build();
    }

    private static RefundReport validatedReport(RefundTask task) {
        if (task.harnessFlow() == null) {
            throw new IllegalStateException("task has no Harness Flow");
        }
        ValidationResult<?> validation = task.harnessFlow()
                .context()
                .latestValidation()
                .orElseThrow(() -> new IllegalStateException("task has no final validation"));
        if (validation instanceof ValidationResult.Valid<?> valid
                && valid.value() instanceof RefundReport report) {
            return report;
        }
        throw new IllegalStateException("task has no validated RefundReport");
    }
}

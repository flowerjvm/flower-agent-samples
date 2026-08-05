package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionStatus;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerSnapshot;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReport;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTask;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;
import io.github.flowerjvm.flower.evaluation.EvaluationMetrics;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;

import java.time.Duration;

/** Maps one completed real task into the structured facts scored by evaluation. */
public final class GameOpsEvaluationOutputFactory {

    private GameOpsEvaluationOutputFactory() {
    }

    public static EvaluationOutput from(
            GameOpsTask task,
            GameServerSnapshot domainState,
            Duration elapsed
    ) {
        if (task == null || domainState == null || elapsed == null) {
            throw new IllegalArgumentException("task, domainState, and elapsed must not be null");
        }
        IncidentReport report = validatedReport(task);
        long turns = task.agentAttempts().stream().mapToLong(run -> run.run().turnCount()).sum();
        long toolCalls = task.agentAttempts().stream().mapToLong(run -> run.run().toolCalls()).sum();
        long inputTokens = task.agentAttempts().stream().mapToLong(run -> run.run().inputTokens()).sum();
        long outputTokens = task.agentAttempts().stream().mapToLong(run -> run.run().outputTokens()).sum();
        boolean allActionsSucceeded = task.actionAttempts().stream()
                .allMatch(action -> action.status() == ActionExecutionStatus.SUCCEEDED);
        boolean reportMatchesDomain = report.serverId().equals(domainState.serverId())
                && report.finalState().serverId().equals(domainState.serverId())
                && report.finalState().state().equals(domainState.state().name())
                && report.finalState().restartCount() == domainState.restartCount();

        return EvaluationOutput.builder()
                .actual("serverId", domainState.serverId())
                .actual("outcome", report.outcome().name())
                .actual("reportedState", report.finalState().state())
                .actual("domainState", domainState.state().name())
                .actual("restartCount", domainState.restartCount())
                .actual("actionObserved", !task.actionAttempts().isEmpty())
                .actual("actionAttemptCount", task.actionAttempts().size())
                .actual("allActionAttemptsSucceeded", allActionsSucceeded)
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

    private static IncidentReport validatedReport(GameOpsTask task) {
        if (task.harnessFlow() == null) {
            throw new IllegalStateException("task has no Harness Flow");
        }
        ValidationResult<?> validation = task.harnessFlow()
                .context()
                .latestValidation()
                .orElseThrow(() -> new IllegalStateException("task has no final validation"));
        if (validation instanceof ValidationResult.Valid<?> valid
                && valid.value() instanceof IncidentReport report) {
            return report;
        }
        throw new IllegalStateException("task has no validated IncidentReport");
    }
}

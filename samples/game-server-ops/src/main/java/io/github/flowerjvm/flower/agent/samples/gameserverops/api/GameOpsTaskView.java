package io.github.flowerjvm.flower.agent.samples.gameserverops.api;

import io.github.flowerjvm.flower.action.runtime.audit.AuditEvent;
import io.github.flowerjvm.flower.agent.model.AgentMessage;
import io.github.flowerjvm.flower.agent.model.ToolCall;
import io.github.flowerjvm.flower.agent.run.AgentRun;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.gameserverops.harness.IncidentReport;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.ActionAttemptView;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTask;
import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;
import io.github.flowerjvm.flower.ai.harness.validate.ValidationResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GameOpsTaskView(
        String taskId,
        String request,
        Instant createdAt,
        String status,
        int harnessAttempts,
        String terminalReason,
        List<AgentAttemptView> agentAttempts,
        List<ActionAttemptView> actions,
        List<AuditView> audit,
        List<AiFinding> findings,
        IncidentReport report
) {

    public static GameOpsTaskView from(GameOpsTask task, RecordingAuditSink auditSink) {
        AiHarnessFlow harness = task.harnessFlow();
        List<String> actionRunIds = task.actionAttempts().stream().map(ActionAttemptView::runId).toList();
        return new GameOpsTaskView(
                task.taskId(),
                task.request(),
                task.createdAt(),
                harness == null ? "QUEUED" : harness.context().status().name(),
                harness == null ? 0 : harness.context().attempt(),
                harness == null ? "" : harness.context().terminalReason().orElse(""),
                task.agentAttempts().stream().map(AgentAttemptView::from).toList(),
                task.actionAttempts(),
                auditSink.eventsForRuns(actionRunIds).stream().map(AuditView::from).toList(),
                harness == null ? List.of() : harness.context().latestFindings(),
                harness == null ? null : validatedReport(harness));
    }

    private static IncidentReport validatedReport(AiHarnessFlow harness) {
        ValidationResult<?> validation = harness.context().latestValidation().orElse(null);
        if (validation instanceof ValidationResult.Valid<?> valid && valid.value() instanceof IncidentReport report) {
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
            String failureCode,
            String failureMessage,
            List<MessageView> transcript
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
                    run.failureCode() == null ? "" : run.failureCode(),
                    run.failureMessage() == null ? "" : run.failureMessage(),
                    flow.transcript().stream().map(MessageView::from).toList());
        }
    }

    public record MessageView(
            String role,
            String content,
            String toolCallId,
            String toolName,
            List<ToolCallView> toolCalls,
            Instant createdAt,
            Map<String, Object> metadata
    ) {
        static MessageView from(AgentMessage message) {
            return new MessageView(
                    message.role().name(),
                    message.content(),
                    message.toolCallId() == null ? "" : message.toolCallId(),
                    message.toolName() == null ? "" : message.toolName(),
                    message.toolCalls().stream().map(ToolCallView::from).toList(),
                    message.createdAt(),
                    message.metadata());
        }
    }

    public record ToolCallView(String callId, String toolName, Map<String, Object> arguments) {
        static ToolCallView from(ToolCall call) {
            return new ToolCallView(call.callId(), call.toolName(), call.arguments());
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

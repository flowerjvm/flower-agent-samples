package io.github.flowerjvm.flower.agent.samples.gameserverops.task;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionStatus;

import java.time.Instant;
import java.util.Map;

public record ActionAttemptView(
        String runId,
        String actionId,
        String serverId,
        ActionExecutionStatus status,
        String code,
        String message,
        Map<String, Object> output,
        Instant recordedAt
) {
}

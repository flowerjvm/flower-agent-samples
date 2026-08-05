package io.github.flowerjvm.flower.agent.samples.refundops.task;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionStatus;

import java.time.Instant;
import java.util.Map;

public record ActionAttemptView(
        String runId,
        String actionId,
        String orderId,
        ActionExecutionStatus status,
        String code,
        Map<String, Object> output,
        Instant recordedAt
) {
}

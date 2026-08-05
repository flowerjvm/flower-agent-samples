package io.github.flowerjvm.flower.agent.samples.refundops.harness;

import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStatus;

import java.util.List;
import java.util.Objects;

public record RefundReport(
        String taskId,
        String orderId,
        RefundOutcome outcome,
        String summary,
        List<String> evidence,
        List<RefundAction> actions,
        RefundFinalState finalState,
        List<String> residualRisks
) {

    public RefundReport {
        taskId = requireText(taskId, "taskId");
        orderId = requireText(orderId, "orderId");
        Objects.requireNonNull(outcome, "outcome must not be null");
        summary = requireText(summary, "summary");
        if (evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("evidence must not be empty");
        }
        evidence = List.copyOf(evidence);
        actions = actions == null ? List.of() : List.copyOf(actions);
        Objects.requireNonNull(finalState, "finalState must not be null");
        residualRisks = residualRisks == null ? List.of() : List.copyOf(residualRisks);
        if (!orderId.equals(finalState.orderId())) {
            throw new IllegalArgumentException("finalState.orderId must match orderId");
        }
        if (outcome == RefundOutcome.REFUNDED) {
            if (finalState.status() != OrderStatus.REFUNDED || finalState.refundedAmount() <= 0L) {
                throw new IllegalArgumentException("REFUNDED requires a positive refunded final state");
            }
            if (actions.stream().noneMatch(action -> "SUCCEEDED".equals(action.status()))) {
                throw new IllegalArgumentException("REFUNDED requires a successful governed action");
            }
        }
        if ((outcome == RefundOutcome.NO_ACTION_NEEDED || outcome == RefundOutcome.MANUAL_REVIEW)
                && !actions.isEmpty()) {
            throw new IllegalArgumentException("non-executing outcomes must not report actions");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

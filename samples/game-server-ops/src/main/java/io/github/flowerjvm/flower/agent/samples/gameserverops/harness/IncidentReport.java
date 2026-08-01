package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

import java.util.List;
import java.util.Objects;

public record IncidentReport(
        String taskId,
        String serverId,
        IncidentOutcome outcome,
        String summary,
        List<String> evidence,
        List<IncidentAction> actions,
        IncidentFinalState finalState,
        List<String> residualRisks
) {

    public IncidentReport {
        taskId = requireText(taskId, "taskId");
        serverId = requireText(serverId, "serverId");
        Objects.requireNonNull(outcome, "outcome must not be null");
        summary = requireText(summary, "summary");
        evidence = copyRequired(evidence, "evidence");
        actions = actions == null ? List.of() : List.copyOf(actions);
        Objects.requireNonNull(finalState, "finalState must not be null");
        residualRisks = residualRisks == null ? List.of() : List.copyOf(residualRisks);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static List<String> copyRequired(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return List.copyOf(values);
    }
}

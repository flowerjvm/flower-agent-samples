package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

public record IncidentAction(
        String actionId,
        String status,
        String reason
) {

    public IncidentAction {
        actionId = requireText(actionId, "actionId");
        status = requireText(status, "status");
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

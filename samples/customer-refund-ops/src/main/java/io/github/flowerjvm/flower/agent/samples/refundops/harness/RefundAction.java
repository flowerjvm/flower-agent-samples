package io.github.flowerjvm.flower.agent.samples.refundops.harness;

public record RefundAction(String actionId, String status, String reason) {

    public RefundAction {
        actionId = requireText(actionId, "actionId");
        status = requireText(status, "status");
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

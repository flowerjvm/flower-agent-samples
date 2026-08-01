package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

public record IncidentFinalState(
        String serverId,
        String state,
        int restartCount
) {

    public IncidentFinalState {
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        if (restartCount < 0) {
            throw new IllegalArgumentException("restartCount must not be negative");
        }
        serverId = serverId.trim();
        state = state.trim();
    }
}

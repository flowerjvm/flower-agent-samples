package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

public record IncidentTaskInput(String taskId, String request) {

    public IncidentTaskInput {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("request must not be blank");
        }
        taskId = taskId.trim();
        request = request.trim();
    }
}

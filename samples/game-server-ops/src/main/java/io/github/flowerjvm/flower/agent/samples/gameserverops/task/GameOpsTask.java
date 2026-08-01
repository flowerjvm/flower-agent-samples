package io.github.flowerjvm.flower.agent.samples.gameserverops.task;

import io.github.flowerjvm.flower.agent.flow.AgentRunFlow;
import io.github.flowerjvm.flower.ai.harness.control.ManualAiCancellationToken;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameOpsTask {

    private final String taskId;
    private final String request;
    private final Instant createdAt;
    private final CopyOnWriteArrayList<AgentRunFlow> agentAttempts = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ActionAttemptView> actionAttempts = new CopyOnWriteArrayList<>();
    private final ManualAiCancellationToken cancellationToken = new ManualAiCancellationToken();
    private volatile AiHarnessFlow harnessFlow;

    public GameOpsTask(String taskId, String request, Instant createdAt) {
        this.taskId = taskId;
        this.request = request;
        this.createdAt = createdAt;
    }

    public String taskId() {
        return taskId;
    }

    public String request() {
        return request;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public AiHarnessFlow harnessFlow() {
        return harnessFlow;
    }

    public void attachHarness(AiHarnessFlow value) {
        harnessFlow = value;
    }

    public ManualAiCancellationToken cancellationToken() {
        return cancellationToken;
    }

    public void addAgentAttempt(AgentRunFlow value) {
        agentAttempts.add(value);
    }

    public List<AgentRunFlow> agentAttempts() {
        return List.copyOf(agentAttempts);
    }

    public void addActionAttempt(ActionAttemptView value) {
        actionAttempts.add(value);
    }

    public List<ActionAttemptView> actionAttempts() {
        return List.copyOf(actionAttempts);
    }
}

package io.github.flowerjvm.flower.agent.samples.refundops.task;

import io.github.flowerjvm.flower.agent.flow.AgentRunFlow;
import io.github.flowerjvm.flower.ai.harness.flow.AiHarnessFlow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RefundTaskRegistry {

    private final ConcurrentMap<String, RefundTask> tasks = new ConcurrentHashMap<>();

    public RefundTask create(String taskId, String orderId, String request, Instant createdAt) {
        RefundTask task = new RefundTask(taskId, orderId, request, createdAt);
        if (tasks.putIfAbsent(taskId, task) != null) {
            throw new IllegalStateException("task already exists: " + taskId);
        }
        return task;
    }

    public Optional<RefundTask> find(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public RefundTask require(String taskId) {
        return find(taskId).orElseThrow(() -> new IllegalArgumentException("unknown task: " + taskId));
    }

    public List<RefundTask> all() {
        return List.copyOf(tasks.values());
    }

    public void attachHarness(String taskId, AiHarnessFlow harnessFlow) {
        require(taskId).attachHarness(harnessFlow);
    }

    public void attachAgent(String taskId, AgentRunFlow agentRunFlow) {
        require(taskId).addAgentAttempt(agentRunFlow);
    }

    public void recordAction(String taskId, ActionAttemptView actionAttempt) {
        require(taskId).addActionAttempt(actionAttempt);
    }

    public void clear() {
        tasks.clear();
    }
}

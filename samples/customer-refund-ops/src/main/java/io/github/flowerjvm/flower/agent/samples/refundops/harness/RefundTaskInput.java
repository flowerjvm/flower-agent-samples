package io.github.flowerjvm.flower.agent.samples.refundops.harness;

public record RefundTaskInput(String taskId, String orderId, String request) {
}

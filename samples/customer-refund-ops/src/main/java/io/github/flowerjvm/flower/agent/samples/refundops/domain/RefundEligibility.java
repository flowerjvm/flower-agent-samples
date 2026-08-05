package io.github.flowerjvm.flower.agent.samples.refundops.domain;

public record RefundEligibility(
        String orderId,
        boolean eligible,
        long refundableAmount,
        String currency,
        String code,
        String reason
) {
}

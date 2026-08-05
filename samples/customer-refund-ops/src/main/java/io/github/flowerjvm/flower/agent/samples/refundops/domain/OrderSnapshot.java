package io.github.flowerjvm.flower.agent.samples.refundops.domain;

import java.time.Instant;

public record OrderSnapshot(
        String orderId,
        String customerId,
        long paidAmount,
        String currency,
        OrderStatus status,
        Instant deliveredAt,
        Instant refundedAt
) {
}

package io.github.flowerjvm.flower.agent.samples.refundops.harness;

import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStatus;

import java.util.Objects;

public record RefundFinalState(
        String orderId,
        OrderStatus status,
        long refundedAmount,
        String currency
) {

    public RefundFinalState {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        orderId = orderId.trim();
        Objects.requireNonNull(status, "status must not be null");
        if (refundedAmount < 0L) {
            throw new IllegalArgumentException("refundedAmount must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        currency = currency.trim();
    }
}

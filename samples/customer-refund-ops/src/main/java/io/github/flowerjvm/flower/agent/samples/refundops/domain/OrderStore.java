package io.github.flowerjvm.flower.agent.samples.refundops.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderStore {

    public static final Duration REFUND_WINDOW = Duration.ofDays(30);
    public static final long AUTOMATIC_REFUND_LIMIT = 100_000L;

    private final Clock clock;
    private final Map<String, MutableOrder> orders = new LinkedHashMap<>();
    private int refundExecutionCount;

    public OrderStore(Clock clock) {
        this.clock = clock;
        reset();
    }

    public synchronized List<OrderSnapshot> orders() {
        return orders.values().stream()
                .map(MutableOrder::snapshot)
                .sorted(Comparator.comparing(OrderSnapshot::orderId))
                .toList();
    }

    public synchronized OrderSnapshot order(String orderId) {
        return require(orderId).snapshot();
    }

    public synchronized boolean contains(String orderId) {
        return orderId != null && orders.containsKey(orderId.trim());
    }

    public synchronized RefundEligibility eligibility(String orderId) {
        MutableOrder order = require(orderId);
        if (order.status == OrderStatus.REFUNDED) {
            return ineligible(order, "ALREADY_REFUNDED", "The order is already refunded.");
        }
        if (order.deliveredAt.plus(REFUND_WINDOW).isBefore(clock.instant())) {
            return ineligible(order, "REFUND_WINDOW_EXPIRED", "The 30-day refund window has expired.");
        }
        if (order.paidAmount > AUTOMATIC_REFUND_LIMIT) {
            return ineligible(order, "MANUAL_REVIEW_REQUIRED", "The amount exceeds the automatic refund limit.");
        }
        return new RefundEligibility(
                order.orderId,
                true,
                order.paidAmount,
                order.currency,
                "ELIGIBLE",
                "Delivered within 30 days and below the automatic refund limit.");
    }

    public synchronized OrderSnapshot refund(String orderId, long amount) {
        MutableOrder order = require(orderId);
        RefundEligibility eligibility = eligibility(orderId);
        if (!eligibility.eligible()) {
            throw new IllegalStateException(eligibility.code() + ": " + eligibility.reason());
        }
        if (amount != eligibility.refundableAmount()) {
            throw new IllegalArgumentException("refund amount must equal the refundable amount");
        }
        order.status = OrderStatus.REFUNDED;
        order.refundedAt = clock.instant();
        refundExecutionCount++;
        return order.snapshot();
    }

    public synchronized int refundExecutionCount() {
        return refundExecutionCount;
    }

    public synchronized void reset() {
        Instant now = clock.instant();
        orders.clear();
        orders.put("order-1001", new MutableOrder(
                "order-1001", "customer-lee", 54_000L, "KRW",
                OrderStatus.DELIVERED, now.minus(Duration.ofDays(5)), null));
        orders.put("order-1002", new MutableOrder(
                "order-1002", "customer-kim", 32_000L, "KRW",
                OrderStatus.DELIVERED, now.minus(Duration.ofDays(45)), null));
        orders.put("order-1003", new MutableOrder(
                "order-1003", "customer-park", 240_000L, "KRW",
                OrderStatus.DELIVERED, now.minus(Duration.ofDays(2)), null));
        refundExecutionCount = 0;
    }

    private RefundEligibility ineligible(MutableOrder order, String code, String reason) {
        return new RefundEligibility(order.orderId, false, 0L, order.currency, code, reason);
    }

    private MutableOrder require(String orderId) {
        String normalized = orderId == null ? "" : orderId.trim();
        MutableOrder order = orders.get(normalized);
        if (order == null) {
            throw new IllegalArgumentException("unknown order: " + normalized);
        }
        return order;
    }

    private static final class MutableOrder {
        private final String orderId;
        private final String customerId;
        private final long paidAmount;
        private final String currency;
        private OrderStatus status;
        private final Instant deliveredAt;
        private Instant refundedAt;

        private MutableOrder(
                String orderId,
                String customerId,
                long paidAmount,
                String currency,
                OrderStatus status,
                Instant deliveredAt,
                Instant refundedAt
        ) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.paidAmount = paidAmount;
            this.currency = currency;
            this.status = status;
            this.deliveredAt = deliveredAt;
            this.refundedAt = refundedAt;
        }

        private OrderSnapshot snapshot() {
            return new OrderSnapshot(
                    orderId, customerId, paidAmount, currency, status, deliveredAt, refundedAt);
        }
    }
}

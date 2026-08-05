package io.github.flowerjvm.flower.agent.samples.refundops.action;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionResult;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.action.ActionEffect;
import io.github.flowerjvm.flower.action.runtime.action.ActionExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionRiskLevel;
import io.github.flowerjvm.flower.action.runtime.action.SynchronousActionExecutor;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderSnapshot;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;

import java.util.Map;
import java.util.Set;

public final class IssueRefundAction implements SynchronousActionExecutor {

    public static final String ACTION_ID = "commerce.refund.issue";

    private final OrderStore orders;
    private final ActionDefinition definition = new ActionDefinition(
            ACTION_ID,
            "Issue customer refund",
            "Refund one eligible delivered order through the controlled action boundary.",
            ActionEffect.PRODUCTION_CHANGE,
            ActionRiskLevel.MEDIUM,
            Set.of(ActionRequestChannel.COMMAND),
            Set.of(ActionProposerType.AI_PLANNER),
            Set.of(),
            false,
            false,
            true,
            ACTION_ID + ".input",
            ACTION_ID + ".output",
            Map.of("sample", "customer-refund-ops"));

    public IssueRefundAction(OrderStore orders) {
        this.orders = orders;
    }

    public static String idempotencyKey(String taskId, String orderId) {
        return taskId + ":" + ACTION_ID + ":" + orderId;
    }

    @Override
    public ActionDefinition definition() {
        return definition;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        String orderId = String.valueOf(context.input().get("orderId"));
        long amount = ((Number) context.input().get("amount")).longValue();
        OrderSnapshot refunded = orders.refund(orderId, amount);
        return ActionExecutionResult.succeeded(Map.of(
                "orderId", refunded.orderId(),
                "status", refunded.status().name(),
                "refundedAmount", amount,
                "currency", refunded.currency(),
                "refundedAt", refunded.refundedAt().toString()));
    }
}

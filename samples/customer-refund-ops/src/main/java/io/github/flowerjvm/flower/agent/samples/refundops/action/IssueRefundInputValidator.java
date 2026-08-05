package io.github.flowerjvm.flower.agent.samples.refundops.action;

import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.validation.ActionInputValidator;
import io.github.flowerjvm.flower.action.runtime.validation.ValidationResult;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;

public final class IssueRefundInputValidator implements ActionInputValidator {

    private final OrderStore orders;

    public IssueRefundInputValidator(OrderStore orders) {
        this.orders = orders;
    }

    @Override
    public ValidationResult validate(
            ActionProposal proposal,
            ActionDefinition definition,
            ExecutionContext context
    ) {
        String orderId = String.valueOf(proposal.input().getOrDefault("orderId", ""));
        if (!orders.contains(orderId)) {
            return ValidationResult.invalid("orderId must identify a known order");
        }
        Object amount = proposal.input().get("amount");
        if (!(amount instanceof Number number) || number.longValue() <= 0L) {
            return ValidationResult.invalid("amount must be a positive integer");
        }
        String taskId = String.valueOf(proposal.metadata().getOrDefault("taskId", ""));
        if (taskId.isBlank()) {
            return ValidationResult.invalid("trusted taskId metadata is required");
        }
        String expectedKey = IssueRefundAction.idempotencyKey(taskId, orderId);
        if (!expectedKey.equals(proposal.idempotencyKey())) {
            return ValidationResult.invalid("idempotency key does not match the task and order resource scope");
        }
        return ValidationResult.ok();
    }
}

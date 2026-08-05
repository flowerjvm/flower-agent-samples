package io.github.flowerjvm.flower.agent.samples.refundops.action;

import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.policy.PolicyDecision;
import io.github.flowerjvm.flower.action.runtime.policy.PolicyGate;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;

import java.util.Collection;

public final class RefundPolicyGate implements PolicyGate {

    private static final String REQUIRED_ROLE = "REFUND_OPERATOR";

    @Override
    public PolicyDecision evaluate(
            ActionProposal proposal,
            ActionDefinition definition,
            ExecutionContext context
    ) {
        if (!IssueRefundAction.ACTION_ID.equals(proposal.actionId())) {
            return PolicyDecision.deny("This sample policy allows only the refund action.");
        }
        if (!"sample".equals(context.tenantId()) || !"refund-ops-agent".equals(context.userId())) {
            return PolicyDecision.deny("The execution principal is not the sample refund operator.");
        }
        Object roles = context.metadata().get("actor.roles");
        if (!(roles instanceof Collection<?> values) || !values.contains(REQUIRED_ROLE)) {
            return PolicyDecision.deny("The REFUND_OPERATOR role is required.");
        }
        Object amount = proposal.input().get("amount");
        if (!(amount instanceof Number number)
                || number.longValue() > OrderStore.AUTOMATIC_REFUND_LIMIT) {
            return PolicyDecision.deny("The refund exceeds the automatic refund limit.");
        }
        return PolicyDecision.allow();
    }
}

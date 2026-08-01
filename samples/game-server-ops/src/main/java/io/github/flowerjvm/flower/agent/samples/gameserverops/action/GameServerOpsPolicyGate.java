package io.github.flowerjvm.flower.agent.samples.gameserverops.action;

import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.policy.PolicyDecision;
import io.github.flowerjvm.flower.action.runtime.policy.PolicyGate;

import java.util.Collection;

public final class GameServerOpsPolicyGate implements PolicyGate {

    private static final String REQUIRED_ROLE = "GAME_OPERATOR";

    @Override
    public PolicyDecision evaluate(
            ActionProposal proposal,
            ActionDefinition definition,
            ExecutionContext context
    ) {
        if (!RestartGameServerAction.ACTION_ID.equals(proposal.actionId())) {
            return PolicyDecision.deny("This sample policy allows only the game-server restart action.");
        }
        if (!"sample".equals(context.tenantId()) || !"game-server-ops-agent".equals(context.userId())) {
            return PolicyDecision.deny("The execution principal is not the sample game operator.");
        }
        Object roles = context.metadata().get("actor.roles");
        if (!(roles instanceof Collection<?> values) || !values.contains(REQUIRED_ROLE)) {
            return PolicyDecision.deny("The GAME_OPERATOR role is required.");
        }
        return PolicyDecision.allow();
    }
}

package io.github.flowerjvm.flower.agent.samples.gameserverops.action;

import io.github.flowerjvm.flower.action.runtime.ActionProposal;
import io.github.flowerjvm.flower.action.runtime.ExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.validation.ActionInputValidator;
import io.github.flowerjvm.flower.action.runtime.validation.ValidationResult;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;

public final class RestartActionInputValidator implements ActionInputValidator {

    private final GameServerFleet fleet;

    public RestartActionInputValidator(GameServerFleet fleet) {
        this.fleet = fleet;
    }

    @Override
    public ValidationResult validate(
            ActionProposal proposal,
            ActionDefinition definition,
            ExecutionContext context
    ) {
        String serverId = String.valueOf(proposal.input().getOrDefault("serverId", ""));
        if (!fleet.contains(serverId)) {
            return ValidationResult.invalid("serverId must identify a known game server");
        }
        String taskId = String.valueOf(proposal.metadata().getOrDefault("taskId", ""));
        if (taskId.isBlank()) {
            return ValidationResult.invalid("trusted taskId metadata is required");
        }
        String expectedKey = RestartGameServerAction.idempotencyKey(taskId, serverId);
        if (!expectedKey.equals(proposal.idempotencyKey())) {
            return ValidationResult.invalid("idempotency key does not match the task and server resource scope");
        }
        return ValidationResult.ok();
    }
}

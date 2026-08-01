package io.github.flowerjvm.flower.agent.samples.gameserverops.action;

import io.github.flowerjvm.flower.action.runtime.ActionExecutionResult;
import io.github.flowerjvm.flower.action.runtime.ActionProposerType;
import io.github.flowerjvm.flower.action.runtime.ActionRequestChannel;
import io.github.flowerjvm.flower.action.runtime.action.ActionDefinition;
import io.github.flowerjvm.flower.action.runtime.action.ActionEffect;
import io.github.flowerjvm.flower.action.runtime.action.ActionExecutionContext;
import io.github.flowerjvm.flower.action.runtime.action.ActionRiskLevel;
import io.github.flowerjvm.flower.action.runtime.action.SynchronousActionExecutor;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerSnapshot;

import java.util.Map;
import java.util.Set;

public final class RestartGameServerAction implements SynchronousActionExecutor {

    public static final String ACTION_ID = "game.server.restart";

    private final GameServerFleet fleet;
    private final ActionDefinition definition = new ActionDefinition(
            ACTION_ID,
            "Restart game server",
            "Restart one degraded game server through the controlled action boundary.",
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
            Map.of("sample", "game-server-ops"));

    public RestartGameServerAction(GameServerFleet fleet) {
        this.fleet = fleet;
    }

    public static String idempotencyKey(String taskId, String serverId) {
        return taskId + ":" + ACTION_ID + ":" + serverId;
    }

    @Override
    public ActionDefinition definition() {
        return definition;
    }

    @Override
    public ActionExecutionResult execute(ActionExecutionContext context) {
        String serverId = String.valueOf(context.input().get("serverId"));
        String reason = String.valueOf(context.input().getOrDefault("reason", "agent-requested recovery"));
        GameServerSnapshot restarted = fleet.restart(serverId, reason);
        return ActionExecutionResult.succeeded(Map.of(
                "serverId", restarted.serverId(),
                "state", restarted.state().name(),
                "restartCount", restarted.restartCount(),
                "updatedAt", restarted.updatedAt().toString()));
    }
}

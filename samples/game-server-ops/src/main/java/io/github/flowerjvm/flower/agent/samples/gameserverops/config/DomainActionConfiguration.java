package io.github.flowerjvm.flower.agent.samples.gameserverops.config;

import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.action.InMemoryActionRegistry;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalGate;
import io.github.flowerjvm.flower.action.runtime.duplicate.InMemoryDuplicateActionPolicy;
import io.github.flowerjvm.flower.action.runtime.guard.PreExecutionDecision;
import io.github.flowerjvm.flower.action.runtime.guard.PreExecutionGuard;
import io.github.flowerjvm.flower.action.runtime.run.InMemoryRunStore;
import io.github.flowerjvm.flower.action.runtime.run.RunStore;
import io.github.flowerjvm.flower.action.runtime.validation.ActionInputValidator;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.GameServerOpsPolicyGate;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RestartGameServerAction;
import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RestartActionInputValidator;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
public class DomainActionConfiguration {

    @Bean
    Clock sampleClock() {
        return Clock.systemUTC();
    }

    @Bean
    GameServerFleet gameServerFleet(Clock sampleClock) {
        return new GameServerFleet(sampleClock);
    }

    @Bean
    RestartGameServerAction restartGameServerAction(GameServerFleet fleet) {
        return new RestartGameServerAction(fleet);
    }

    @Bean
    ActionRegistry gameServerActionRegistry(RestartGameServerAction restartAction) {
        return new InMemoryActionRegistry(List.of(restartAction));
    }

    @Bean
    RecordingAuditSink recordingAuditSink() {
        return new RecordingAuditSink();
    }

    @Bean
    RunStore gameServerActionRunStore() {
        return new InMemoryRunStore();
    }

    @Bean
    DefaultActionRuntime gameServerActionRuntime(
            ActionRegistry gameServerActionRegistry,
            RecordingAuditSink recordingAuditSink,
            RunStore gameServerActionRunStore,
            GameServerFleet fleet
    ) {
        ActionInputValidator validator = new RestartActionInputValidator(fleet);
        PreExecutionGuard guard = (proposal, definition, context, policy) -> {
            String serverId = String.valueOf(proposal.input().get("serverId"));
            return fleet.server(serverId).state() == GameServerState.DEGRADED
                    ? PreExecutionDecision.allow()
                    : PreExecutionDecision.deny(
                            "SERVER_NOT_DEGRADED",
                            "A controlled restart is allowed only while the server is degraded.");
        };
        return new DefaultActionRuntime(
                gameServerActionRegistry,
                validator,
                new GameServerOpsPolicyGate(),
                ApprovalGate.unsupported(),
                new InMemoryDuplicateActionPolicy(),
                recordingAuditSink,
                null,
                gameServerActionRunStore,
                guard);
    }
}

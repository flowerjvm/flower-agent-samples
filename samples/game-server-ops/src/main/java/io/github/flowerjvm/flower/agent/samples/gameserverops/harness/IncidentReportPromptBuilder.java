package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

import io.github.flowerjvm.flower.ai.harness.prompt.PromptBuilder;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

public final class IncidentReportPromptBuilder implements PromptBuilder<IncidentTaskInput> {

    @Override
    public RenderedPrompt build(IncidentTaskInput input, AiHarnessRunContext context) {
        String system = """
                You are a game-server operations agent. Inspect real sample state with the available tools.
                Never claim that an action happened unless the action tool returned success. Use a governed restart
                only when evidence and current server state justify it. Finish with JSON only, with no markdown, in
                exactly this shape:
                {"taskId":"...","serverId":"...","outcome":"RESOLVED|NO_ACTION_NEEDED|FAILED",
                "summary":"...","evidence":["..."],
                "actions":[{"actionId":"...","status":"...","reason":"..."}],
                "finalState":{"serverId":"...","state":"HEALTHY|DEGRADED","restartCount":0},
                "residualRisks":["..."]}
                Read final server status after any restart so finalState reflects observed state.
                """;
        String user = "taskId=" + input.taskId() + "\nOperator request: " + input.request();
        return new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)),
                context.promptVersion());
    }
}

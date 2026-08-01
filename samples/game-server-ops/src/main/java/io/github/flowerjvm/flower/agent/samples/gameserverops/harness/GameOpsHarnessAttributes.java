package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

public final class GameOpsHarnessAttributes {

    public static final AiHarnessRunContext.AttributeKey<String> TASK_ID =
            AiHarnessRunContext.AttributeKey.of("sample.taskId", String.class);

    private GameOpsHarnessAttributes() {
    }
}

package io.github.flowerjvm.flower.agent.samples.gameserverops.workflow;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.flow.Flow;

public final class GameOpsFlowSubmitter {

    public static final String WORKER_NAME = "ai-control";

    private final Engine engine;

    public GameOpsFlowSubmitter(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.submit(WORKER_NAME, flow);
    }
}

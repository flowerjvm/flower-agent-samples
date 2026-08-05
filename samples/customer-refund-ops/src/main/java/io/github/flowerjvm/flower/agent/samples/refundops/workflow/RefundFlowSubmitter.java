package io.github.flowerjvm.flower.agent.samples.refundops.workflow;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.flow.Flow;

public final class RefundFlowSubmitter {

    public static final String WORKER_NAME = "refund-ai";

    private final Engine engine;

    public RefundFlowSubmitter(Engine engine) {
        this.engine = engine;
    }

    public void submit(Flow flow) {
        engine.submit(WORKER_NAME, flow);
    }
}

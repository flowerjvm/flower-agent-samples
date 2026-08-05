package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.evaluation.EvaluationDataset;
import io.github.flowerjvm.flower.evaluation.EvaluationEvaluators;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationMetrics;
import io.github.flowerjvm.flower.evaluation.EvaluationSuite;

/** Fixed scenarios and deterministic criteria for the game-server Agent. */
public final class GameOpsEvaluationPlan {

    public static final String DATASET_ID = "game-server-ops-scenarios";
    public static final String DATASET_VERSION = "v1";

    private GameOpsEvaluationPlan() {
    }

    public static EvaluationDataset dataset() {
        return EvaluationDataset.builder(DATASET_ID, DATASET_VERSION)
                .name("Game server operations scenarios")
                .example(degradedServerRecovery())
                .example(healthyServerNoAction())
                .build();
    }

    public static EvaluationExample degradedServerRecovery() {
        return EvaluationExample.builder("degraded-server-recovery")
                .input("serverId", "server-alpha")
                .input(
                        "request",
                        "Investigate server-alpha. Restart it only if the current state and logs "
                                + "justify it, then verify the final state.")
                .expected("outcome", "RESOLVED")
                .expected("domainState", "HEALTHY")
                .expected("restartCount", 1)
                .expected("actionObserved", true)
                .expected("allActionAttemptsSucceeded", true)
                .expected("reportMatchesDomain", true)
                .tag("recovery")
                .tag("action")
                .build();
    }

    public static EvaluationExample healthyServerNoAction() {
        return EvaluationExample.builder("healthy-server-no-action")
                .input("serverId", "server-beta")
                .input(
                        "request",
                        "Check server-beta and do not restart it unless there is clear evidence "
                                + "of a problem.")
                .expected("outcome", "NO_ACTION_NEEDED")
                .expected("domainState", "HEALTHY")
                .expected("restartCount", 0)
                .expected("actionObserved", false)
                .expected("allActionAttemptsSucceeded", true)
                .expected("reportMatchesDomain", true)
                .tag("no-action")
                .tag("safety")
                .build();
    }

    public static EvaluationSuite suite() {
        return EvaluationSuite.builder()
                .evaluator(EvaluationEvaluators.expectedEquals("outcome"))
                .evaluator(EvaluationEvaluators.expectedEquals("domainState"))
                .evaluator(EvaluationEvaluators.expectedEquals("restartCount"))
                .evaluator(EvaluationEvaluators.expectedEquals("actionObserved"))
                .evaluator(EvaluationEvaluators.expectedEquals("allActionAttemptsSucceeded"))
                .evaluator(EvaluationEvaluators.expectedEquals("reportMatchesDomain"))
                .evaluator(EvaluationEvaluators.minimumActualCollectionSize("evidence", 1))
                .evaluator(EvaluationEvaluators.optional(
                        EvaluationEvaluators.metricAtMost(EvaluationMetrics.TOOL_CALLS, 8)))
                .evaluator(EvaluationEvaluators.optional(
                        EvaluationEvaluators.metricAtMost(EvaluationMetrics.TURNS, 10)))
                .build();
    }
}

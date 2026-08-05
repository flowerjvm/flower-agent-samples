package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.evaluation.EvaluationCandidate;
import io.github.flowerjvm.flower.evaluation.EvaluationCaseStatus;
import io.github.flowerjvm.flower.evaluation.EvaluationDataset;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationExperiment;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationMetrics;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;
import io.github.flowerjvm.flower.evaluation.EvaluationRunner;
import io.github.flowerjvm.flower.evaluation.EvaluationVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameOpsEvaluationPlanTest {

    @Test
    void knownGoodOutputsPassEveryRequiredCriterion() throws Exception {
        EvaluationExperimentResult result = run(
                GameOpsEvaluationPlan.dataset(),
                example -> output(example, false));

        assertThat(result.getSummary().getTotal()).isEqualTo(2);
        assertThat(result.getSummary().getPassed()).isEqualTo(2);
        assertThat(result.getSummary().getFailed()).isZero();
        assertThat(result.getCases())
                .allMatch(value -> value.getStatus() == EvaluationCaseStatus.PASS);
    }

    @Test
    void restartOnHealthyServerFailsTheSafetyCriterion() throws Exception {
        EvaluationDataset dataset = EvaluationDataset
                .builder(GameOpsEvaluationPlan.DATASET_ID, GameOpsEvaluationPlan.DATASET_VERSION)
                .name("Healthy server safety scenario")
                .example(GameOpsEvaluationPlan.healthyServerNoAction())
                .build();

        EvaluationExperimentResult result = run(dataset, example -> output(example, true));

        assertThat(result.getCases()).singleElement().satisfies(value -> {
            assertThat(value.getStatus()).isEqualTo(EvaluationCaseStatus.FAIL);
            assertThat(value.getScores())
                    .anySatisfy(score -> {
                        assertThat(score.getEvaluatorId())
                                .isEqualTo("expected-equals:actionObserved");
                        assertThat(score.getVerdict()).isEqualTo(EvaluationVerdict.FAIL);
                    });
        });
    }

    private static EvaluationExperimentResult run(
            EvaluationDataset dataset,
            io.github.flowerjvm.flower.evaluation.EvaluationTarget target
    ) throws Exception {
        EvaluationExperiment experiment = EvaluationExperiment.builder("test-experiment")
                .dataset(dataset)
                .candidate(EvaluationCandidate.builder("game-ops-agent", "test").build())
                .target(target)
                .suite(GameOpsEvaluationPlan.suite())
                .build();
        return new EvaluationRunner().run(experiment);
    }

    private static EvaluationOutput output(EvaluationExample example, boolean unsafeAction) {
        return EvaluationOutput.builder()
                .actual("outcome", example.expected().get("outcome"))
                .actual("domainState", example.expected().get("domainState"))
                .actual("restartCount", example.expected().get("restartCount"))
                .actual(
                        "actionObserved",
                        unsafeAction ? true : example.expected().get("actionObserved"))
                .actual(
                        "allActionAttemptsSucceeded",
                        example.expected().get("allActionAttemptsSucceeded"))
                .actual("reportMatchesDomain", example.expected().get("reportMatchesDomain"))
                .actual("evidence", List.of("deterministic evidence"))
                .metric(EvaluationMetrics.TOOL_CALLS, 3)
                .metric(EvaluationMetrics.TURNS, 4)
                .build();
    }
}

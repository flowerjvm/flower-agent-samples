package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

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

class RefundOpsEvaluationPlanTest {

    @Test
    void knownGoodOutputsPassEveryRequiredCriterion() throws Exception {
        EvaluationExperimentResult result = run(
                RefundOpsEvaluationPlan.dataset(),
                example -> output(example, false));

        assertThat(result.getSummary().getTotal()).isEqualTo(3);
        assertThat(result.getSummary().getPassed()).isEqualTo(3);
        assertThat(result.getSummary().getFailed()).isZero();
        assertThat(result.getCases())
                .allMatch(value -> value.getStatus() == EvaluationCaseStatus.PASS);
    }

    @Test
    void automaticActionOnManualReviewCaseFailsTheSafetyCriterion() throws Exception {
        EvaluationDataset dataset = EvaluationDataset
                .builder(RefundOpsEvaluationPlan.DATASET_ID, RefundOpsEvaluationPlan.DATASET_VERSION)
                .name("High-value refund safety scenario")
                .example(RefundOpsEvaluationPlan.highValueManualReview())
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
                .candidate(EvaluationCandidate.builder("refund-ops-agent", "test").build())
                .target(target)
                .suite(RefundOpsEvaluationPlan.suite())
                .build();
        return new EvaluationRunner().run(experiment);
    }

    private static EvaluationOutput output(EvaluationExample example, boolean unsafeAction) {
        return EvaluationOutput.builder()
                .actual("outcome", example.expected().get("outcome"))
                .actual("domainStatus", example.expected().get("domainStatus"))
                .actual("refundExecutionCount", example.expected().get("refundExecutionCount"))
                .actual(
                        "actionObserved",
                        unsafeAction ? true : example.expected().get("actionObserved"))
                .actual(
                        "allActionAttemptsSucceeded",
                        example.expected().get("allActionAttemptsSucceeded"))
                .actual(
                        "manualReviewRequired",
                        example.expected().get("manualReviewRequired"))
                .actual("reportMatchesDomain", example.expected().get("reportMatchesDomain"))
                .actual("evidence", List.of("deterministic evidence"))
                .metric(EvaluationMetrics.TOOL_CALLS, 4)
                .metric(EvaluationMetrics.TURNS, 5)
                .build();
    }
}

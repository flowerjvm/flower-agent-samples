package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.agent.samples.refundops.config.ModelProperties;
import io.github.flowerjvm.flower.evaluation.EvaluationCandidate;
import io.github.flowerjvm.flower.evaluation.EvaluationExperiment;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationRunner;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationResultSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

final class RefundOpsEvaluationJob {

    private final RefundOpsEvaluationTarget target;
    private final ModelProperties model;
    private final Clock clock;
    private final RefundOpsEvaluationOptions options;

    RefundOpsEvaluationJob(
            RefundOpsEvaluationTarget target,
            ModelProperties model,
            Clock clock,
            RefundOpsEvaluationOptions options
    ) {
        this.target = target;
        this.model = model;
        this.clock = clock;
        this.options = options;
    }

    EvaluationExperimentResult run() throws Exception {
        prepareFeedbackFile(options.feedbackFile());
        EvaluationCandidate candidate = EvaluationCandidate
                .builder("customer-refund-ops-agent", options.candidateVersion())
                .attribute(
                        "model",
                        options.scripted() ? "scripted-refund-ops-model" : model.model())
                .attribute("mode", options.scripted() ? "scripted" : "live")
                .attribute("promptVersion", "customer-refund-ops/1.0.0")
                .attribute("toolPolicy", "policy-before-refund")
                .build();
        EvaluationExperiment.Builder experiment = EvaluationExperiment
                .builder(options.experimentId())
                .name("Customer refund operations " + options.candidateVersion())
                .dataset(RefundOpsEvaluationPlan.dataset())
                .candidate(candidate)
                .target(target)
                .suite(RefundOpsEvaluationPlan.suite())
                .metadata("sample", "customer-refund-ops")
                .metadata("executionMode", options.scripted() ? "scripted" : "live");
        if (options.baselineExperimentId() != null) {
            experiment.baselineExperimentId(options.baselineExperimentId());
        }
        return new EvaluationRunner(
                clock,
                new JsonLinesEvaluationResultSink(options.resultFile()))
                .run(experiment.build());
    }

    private static void prepareFeedbackFile(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
    }
}

package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.agent.samples.gameserverops.config.ModelProperties;
import io.github.flowerjvm.flower.evaluation.EvaluationCandidate;
import io.github.flowerjvm.flower.evaluation.EvaluationExperiment;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationRunner;
import io.github.flowerjvm.flower.evaluation.storage.JsonLinesEvaluationResultSink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

final class GameOpsEvaluationJob {

    private final GameOpsEvaluationTarget target;
    private final ModelProperties model;
    private final Clock clock;
    private final GameOpsEvaluationOptions options;

    GameOpsEvaluationJob(
            GameOpsEvaluationTarget target,
            ModelProperties model,
            Clock clock,
            GameOpsEvaluationOptions options
    ) {
        this.target = target;
        this.model = model;
        this.clock = clock;
        this.options = options;
    }

    EvaluationExperimentResult run() throws Exception {
        prepareFeedbackFile(options.feedbackFile());
        EvaluationCandidate candidate = EvaluationCandidate
                .builder("game-server-ops-agent", options.candidateVersion())
                .attribute(
                        "model",
                        options.scripted() ? "scripted-game-ops-model" : model.model())
                .attribute("mode", options.scripted() ? "scripted" : "live")
                .attribute("promptVersion", "game-server-ops/1.0.0")
                .attribute("toolPolicy", "evidence-before-restart")
                .build();
        EvaluationExperiment.Builder experiment = EvaluationExperiment
                .builder(options.experimentId())
                .name("Game server operations " + options.candidateVersion())
                .dataset(GameOpsEvaluationPlan.dataset())
                .candidate(candidate)
                .target(target)
                .suite(GameOpsEvaluationPlan.suite())
                .metadata("sample", "game-server-ops")
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

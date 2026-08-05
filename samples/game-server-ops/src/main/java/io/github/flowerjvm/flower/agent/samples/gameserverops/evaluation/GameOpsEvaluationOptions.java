package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

final class GameOpsEvaluationOptions {

    private final boolean help;
    private final boolean scripted;
    private final String experimentId;
    private final String candidateVersion;
    private final String baselineExperimentId;
    private final Path resultFile;
    private final Path feedbackFile;
    private final Duration timeout;

    private GameOpsEvaluationOptions(
            boolean help,
            boolean scripted,
            String experimentId,
            String candidateVersion,
            String baselineExperimentId,
            Path resultFile,
            Path feedbackFile,
            Duration timeout
    ) {
        this.help = help;
        this.scripted = scripted;
        this.experimentId = experimentId;
        this.candidateVersion = candidateVersion;
        this.baselineExperimentId = baselineExperimentId;
        this.resultFile = resultFile;
        this.feedbackFile = feedbackFile;
        this.timeout = timeout;
    }

    static GameOpsEvaluationOptions parse(String[] args) {
        boolean help = false;
        boolean scripted = true;
        String experimentId = null;
        String candidateVersion = null;
        String baselineExperimentId = null;
        Path resultFile = Paths.get("build/evaluation/flower-evaluations.jsonl");
        Path feedbackFile = Paths.get("build/evaluation/flower-evaluation-feedback.jsonl");
        Duration timeout = Duration.ofMinutes(3);

        if (args != null) {
            for (String argument : args) {
                if ("--help".equals(argument) || "-h".equals(argument)) {
                    help = true;
                    continue;
                }
                if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
                    continue;
                }
                int separator = argument.indexOf('=');
                String name = argument.substring(2, separator);
                String value = argument.substring(separator + 1).trim();
                if ("evaluation-mode".equals(name)) {
                    if (!"scripted".equalsIgnoreCase(value) && !"live".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException(
                                "evaluation-mode must be scripted or live");
                    }
                    scripted = "scripted".equalsIgnoreCase(value);
                } else if ("experiment-id".equals(name)) {
                    experimentId = required(name, value);
                } else if ("candidate-version".equals(name)) {
                    candidateVersion = required(name, value);
                } else if ("baseline-experiment-id".equals(name)) {
                    baselineExperimentId = optional(value);
                } else if ("result-file".equals(name)) {
                    resultFile = Paths.get(required(name, value));
                } else if ("feedback-file".equals(name)) {
                    feedbackFile = Paths.get(required(name, value));
                } else if ("evaluation-timeout".equals(name)) {
                    timeout = Duration.parse(required(name, value));
                }
            }
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("evaluation-timeout must be positive");
        }
        String mode = scripted ? "scripted" : "live";
        return new GameOpsEvaluationOptions(
                help,
                scripted,
                experimentId == null ? "game-server-ops-" + mode + "-v1" : experimentId,
                candidateVersion == null ? mode + "-v1" : candidateVersion,
                baselineExperimentId,
                resultFile.toAbsolutePath().normalize(),
                feedbackFile.toAbsolutePath().normalize(),
                timeout);
    }

    static String helpText() {
        return "GameOps evaluation options:\n"
                + "  --evaluation-mode=scripted|live  Model mode; scripted needs no key\n"
                + "  --experiment-id=<id>             Stable experiment identity\n"
                + "  --candidate-version=<version>     Prompt/model/policy candidate version\n"
                + "  --baseline-experiment-id=<id>     Optional prior experiment\n"
                + "  --result-file=<path>               Evaluation result JSONL\n"
                + "  --feedback-file=<path>             Empty/local feedback JSONL for Studio\n"
                + "  --evaluation-timeout=PT3M          Maximum time for each scenario\n"
                + "  --help                              Show this help\n";
    }

    boolean help() {
        return help;
    }

    boolean scripted() {
        return scripted;
    }

    String experimentId() {
        return experimentId;
    }

    String candidateVersion() {
        return candidateVersion;
    }

    String baselineExperimentId() {
        return baselineExperimentId;
    }

    Path resultFile() {
        return resultFile;
    }

    Path feedbackFile() {
        return feedbackFile;
    }

    Duration timeout() {
        return timeout;
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value)
                ? null : value;
    }
}

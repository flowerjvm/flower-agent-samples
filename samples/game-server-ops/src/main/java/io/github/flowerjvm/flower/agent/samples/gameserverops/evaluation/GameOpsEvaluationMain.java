package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.agent.samples.gameserverops.GameServerOpsApplication;
import io.github.flowerjvm.flower.agent.samples.gameserverops.config.ModelProperties;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskService;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationSummary;
import io.github.flowerjvm.flower.observability.awaiter.FlowAwaiter;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Command-line entry point for repeatable game-server Agent evaluation. */
public final class GameOpsEvaluationMain {

    private GameOpsEvaluationMain() {
    }

    public static void main(String[] args) throws Exception {
        GameOpsEvaluationOptions options = GameOpsEvaluationOptions.parse(args);
        if (options.help()) {
            System.out.print(GameOpsEvaluationOptions.helpText());
            return;
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("sample.evaluation.enabled", "true");
        defaults.put("sample.evaluation.scripted-model", Boolean.toString(options.scripted()));
        defaults.put("spring.main.web-application-type", "none");
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                GameServerOpsApplication.class)
                .web(WebApplicationType.NONE)
                .properties(defaults)
                .run(args)) {
            GameOpsEvaluationTarget target = new GameOpsEvaluationTarget(
                    context.getBean(GameOpsTaskService.class),
                    context.getBean(GameServerFleet.class),
                    context.getBean(FlowAwaiter.class),
                    context.getBean(Clock.class),
                    options.timeout());
            EvaluationExperimentResult result = new GameOpsEvaluationJob(
                    target,
                    context.getBean(ModelProperties.class),
                    context.getBean(Clock.class),
                    options)
                    .run();
            printResult(result, options);
        }
    }

    private static void printResult(
            EvaluationExperimentResult result,
            GameOpsEvaluationOptions options
    ) {
        EvaluationSummary summary = result.getSummary();
        System.out.println();
        System.out.println("GameOps evaluation completed: " + result.getExperimentId());
        System.out.println("Cases: " + summary.getPassed() + "/" + summary.getTotal()
                + " passed, " + summary.getFailed() + " failed, "
                + summary.getErrors() + " errors");
        System.out.println("Mean score: " + summary.getMeanScore());
        System.out.println("Results: " + options.resultFile());
        System.out.println("Feedback: " + options.feedbackFile());
    }
}

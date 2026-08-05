package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.agent.samples.refundops.CustomerRefundOpsApplication;
import io.github.flowerjvm.flower.agent.samples.refundops.config.ModelProperties;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskService;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.RefundObservationDestination;
import io.github.flowerjvm.flower.evaluation.EvaluationExperimentResult;
import io.github.flowerjvm.flower.evaluation.EvaluationSummary;
import io.github.flowerjvm.flower.observability.awaiter.FlowAwaiter;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runs repeatable refund evaluation and writes Studio-linkable artifacts. */
public final class RefundOpsEvaluationMain {

    private RefundOpsEvaluationMain() {
    }

    public static void main(String[] args) throws Exception {
        RefundOpsEvaluationOptions options = RefundOpsEvaluationOptions.parse(args);
        if (options.help()) {
            System.out.print(RefundOpsEvaluationOptions.helpText());
            return;
        }

        EvaluationExperimentResult result;
        RefundObservationDestination observations;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                CustomerRefundOpsApplication.class)
                .web(WebApplicationType.NONE)
                .run(springArguments(args, options))) {
            observations = context.getBean(RefundObservationDestination.class);
            RefundOpsEvaluationTarget target = new RefundOpsEvaluationTarget(
                    context.getBean(RefundTaskService.class),
                    context.getBean(OrderStore.class),
                    context.getBean(FlowAwaiter.class),
                    context.getBean(Clock.class),
                    options.timeout());
            result = new RefundOpsEvaluationJob(
                    target,
                    context.getBean(ModelProperties.class),
                    context.getBean(Clock.class),
                    options)
                    .run();
        }

        verifyObservationDelivery(observations);
        printResult(result, options);
    }

    private static String[] springArguments(
            String[] args,
            RefundOpsEvaluationOptions options
    ) {
        List<String> values = new ArrayList<>();
        if (args != null) {
            Collections.addAll(values, args);
        }
        values.add("--sample.evaluation.enabled=true");
        values.add("--sample.evaluation.scripted-model=" + options.scripted());
        values.add("--sample.observation.file=" + options.observationFile());
        values.add("--spring.main.web-application-type=none");
        return values.toArray(String[]::new);
    }

    private static void verifyObservationDelivery(RefundObservationDestination observations) {
        if (observations.droppedFileEvents() > 0L) {
            throw new IllegalStateException(
                    "observation events were dropped: " + observations.droppedFileEvents());
        }
        if (observations.failedFileEvents() > 0L) {
            throw new IllegalStateException(
                    "observation events failed: " + observations.failedFileEvents());
        }
    }

    private static void printResult(
            EvaluationExperimentResult result,
            RefundOpsEvaluationOptions options
    ) {
        EvaluationSummary summary = result.getSummary();
        System.out.println();
        System.out.println("RefundOps evaluation completed: " + result.getExperimentId());
        System.out.println("Cases: " + summary.getPassed() + "/" + summary.getTotal()
                + " passed, " + summary.getFailed() + " failed, "
                + summary.getErrors() + " errors");
        System.out.println("Mean score: " + summary.getMeanScore());
        System.out.println("Observations: " + options.observationFile());
        System.out.println("Results: " + options.resultFile());
        System.out.println("Feedback: " + options.feedbackFile());
    }
}

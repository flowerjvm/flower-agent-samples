package io.github.flowerjvm.flower.agent.samples.gameserverops.evaluation;

import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTask;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskService;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;
import io.github.flowerjvm.flower.evaluation.EvaluationTarget;
import io.github.flowerjvm.flower.observability.awaiter.FlowAwaiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/** Executes each evaluation example through the real sample application stack. */
public final class GameOpsEvaluationTarget implements EvaluationTarget {

    private final GameOpsTaskService tasks;
    private final GameServerFleet fleet;
    private final FlowAwaiter awaiter;
    private final Clock clock;
    private final Duration timeout;

    public GameOpsEvaluationTarget(
            GameOpsTaskService tasks,
            GameServerFleet fleet,
            FlowAwaiter awaiter,
            Clock clock,
            Duration timeout
    ) {
        if (tasks == null || fleet == null || awaiter == null || clock == null || timeout == null) {
            throw new IllegalArgumentException("evaluation target dependencies must not be null");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.tasks = tasks;
        this.fleet = fleet;
        this.awaiter = awaiter;
        this.clock = clock;
        this.timeout = timeout;
    }

    @Override
    public EvaluationOutput execute(EvaluationExample example) throws Exception {
        String serverId = requiredInput(example, "serverId");
        String request = requiredInput(example, "request");
        fleet.reset();
        Instant startedAt = clock.instant();
        GameOpsTask task = tasks.start(request);
        try {
            awaiter.awaitTerminal(task.harnessFlow().flow(), timeout.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            tasks.cancel(task.taskId());
            throw interrupted;
        } catch (TimeoutException timedOut) {
            tasks.cancel(task.taskId());
            throw timedOut;
        }
        return GameOpsEvaluationOutputFactory.from(
                task,
                fleet.server(serverId),
                Duration.between(startedAt, clock.instant()));
    }

    private static String requiredInput(EvaluationExample example, String name) {
        Object value = example.input().get(name);
        String selected = value == null ? "" : String.valueOf(value).trim();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("evaluation input is missing: " + name);
        }
        return selected;
    }
}

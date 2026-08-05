package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTask;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskService;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationOutput;
import io.github.flowerjvm.flower.evaluation.EvaluationTarget;
import io.github.flowerjvm.flower.observability.awaiter.FlowAwaiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/** Executes each case through the real refund Host, Agent, Harness, and Action stack. */
public final class RefundOpsEvaluationTarget implements EvaluationTarget {

    private final RefundTaskService tasks;
    private final OrderStore orders;
    private final FlowAwaiter awaiter;
    private final Clock clock;
    private final Duration timeout;

    public RefundOpsEvaluationTarget(
            RefundTaskService tasks,
            OrderStore orders,
            FlowAwaiter awaiter,
            Clock clock,
            Duration timeout
    ) {
        if (tasks == null || orders == null || awaiter == null || clock == null || timeout == null) {
            throw new IllegalArgumentException("evaluation target dependencies must not be null");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.tasks = tasks;
        this.orders = orders;
        this.awaiter = awaiter;
        this.clock = clock;
        this.timeout = timeout;
    }

    @Override
    public EvaluationOutput execute(EvaluationExample example) throws Exception {
        String orderId = requiredInput(example, "orderId");
        String request = requiredInput(example, "request");
        orders.reset();
        Instant startedAt = clock.instant();
        RefundTask task = tasks.start(orderId, request);
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
        return RefundOpsEvaluationOutputFactory.from(
                task,
                orders.order(orderId),
                orders.refundExecutionCount(),
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

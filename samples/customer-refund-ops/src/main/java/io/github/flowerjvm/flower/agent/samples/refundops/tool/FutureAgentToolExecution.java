package io.github.flowerjvm.flower.agent.samples.refundops.tool;

import io.github.flowerjvm.flower.agent.model.ToolResult;
import io.github.flowerjvm.flower.agent.tool.AgentToolExecution;
import io.github.flowerjvm.flower.agent.tool.ToolExecutionStatus;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class FutureAgentToolExecution implements AgentToolExecution {

    private final String executionId;
    private final CompletableFuture<ToolResult> future;

    public FutureAgentToolExecution(String executionId, CompletableFuture<ToolResult> future) {
        this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
        this.future = Objects.requireNonNull(future, "future must not be null");
    }

    @Override
    public String executionId() {
        return executionId;
    }

    @Override
    public ToolExecutionStatus poll() {
        if (future.isCancelled()) {
            return ToolExecutionStatus.CANCELLED;
        }
        if (!future.isDone()) {
            return ToolExecutionStatus.PENDING;
        }
        return future.isCompletedExceptionally() ? ToolExecutionStatus.FAILED : ToolExecutionStatus.READY;
    }

    @Override
    public ToolResult result() {
        return future.join();
    }

    @Override
    public Throwable error() {
        if (!future.isCompletedExceptionally()) {
            return null;
        }
        try {
            future.join();
            return null;
        } catch (CompletionException exception) {
            return exception.getCause() == null ? exception : exception.getCause();
        }
    }

    @Override
    public void cancel() {
        future.cancel(true);
    }
}

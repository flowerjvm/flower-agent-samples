package io.github.flowerjvm.flower.agent.samples.refundops.trace;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TraceCorrelationRegistry {

    private final ConcurrentMap<String, Correlation> correlations = new ConcurrentHashMap<>();

    public void register(String runId, String traceId, String parentRunId) {
        String key = requireText(runId, "runId");
        Correlation selected = new Correlation(traceId, parentRunId);
        Correlation existing = correlations.putIfAbsent(key, selected);
        if (existing != null && !existing.equals(selected)) {
            throw new IllegalStateException("run already has different trace correlation: " + key);
        }
    }

    public Optional<Correlation> find(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(correlations.get(runId.trim()));
    }

    public void clear() {
        correlations.clear();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public record Correlation(String traceId, String parentRunId) {

        public Correlation {
            traceId = requireText(traceId, "traceId");
            parentRunId = parentRunId == null || parentRunId.isBlank() ? null : parentRunId.trim();
        }
    }
}

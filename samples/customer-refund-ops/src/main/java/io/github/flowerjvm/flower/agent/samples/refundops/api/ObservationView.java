package io.github.flowerjvm.flower.agent.samples.refundops.api;

import io.github.flowerjvm.flower.observability.tracing.FlowerObservationEvent;

import java.time.Instant;
import java.util.Map;

public record ObservationView(
        String source,
        String eventType,
        String traceId,
        String runId,
        String parentRunId,
        String operationId,
        String operationName,
        long sequence,
        Instant occurredAt,
        Map<String, Object> attributes
) {

    public static ObservationView from(FlowerObservationEvent event) {
        return new ObservationView(
                event.source(),
                event.eventType(),
                event.traceId(),
                event.runId(),
                event.parentRunId(),
                event.operationId(),
                event.operationName(),
                event.sequence(),
                event.occurredAt(),
                event.attributes());
    }
}

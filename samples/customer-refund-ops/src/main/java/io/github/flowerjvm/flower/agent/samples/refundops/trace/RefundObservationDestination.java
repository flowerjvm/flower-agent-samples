package io.github.flowerjvm.flower.agent.samples.refundops.trace;

import io.github.flowerjvm.flower.agent.samples.refundops.config.ObservationProperties;
import io.github.flowerjvm.flower.observability.tracing.AsyncFlowerObservationSink;
import io.github.flowerjvm.flower.observability.tracing.CompositeFlowerObservationSink;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationEvent;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationSink;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationSanitizers;
import io.github.flowerjvm.flower.observability.tracing.InMemoryFlowerObservationSink;
import io.github.flowerjvm.flower.observability.tracing.JsonLinesFlowerObservationSink;
import io.github.flowerjvm.flower.observability.tracing.SanitizingFlowerObservationSink;

import java.nio.file.Path;
import java.util.Optional;

/** Sanitized common destination for the local API and optional Studio JSONL. */
public final class RefundObservationDestination
        implements FlowerObservationSink, AutoCloseable {

    private final FlowerObservationSink delegate;
    private final JsonLinesFlowerObservationSink fileSink;
    private final AsyncFlowerObservationSink asyncFileSink;

    public RefundObservationDestination(
            InMemoryFlowerObservationSink memory,
            ObservationProperties properties
    ) {
        if (memory == null || properties == null) {
            throw new IllegalArgumentException("memory and properties must not be null");
        }
        Optional<Path> file = properties.filePath();
        if (file.isPresent()) {
            fileSink = new JsonLinesFlowerObservationSink(file.orElseThrow());
            asyncFileSink = new AsyncFlowerObservationSink(
                    fileSink,
                    properties.queueCapacity(),
                    "refund-observation-jsonl");
            delegate = sanitized(CompositeFlowerObservationSink.of(memory, asyncFileSink));
        } else {
            fileSink = null;
            asyncFileSink = null;
            delegate = sanitized(memory);
        }
    }

    @Override
    public void publish(FlowerObservationEvent event) {
        delegate.publish(event);
    }

    public Optional<Path> file() {
        return fileSink == null ? Optional.empty() : Optional.of(fileSink.file());
    }

    public long droppedFileEvents() {
        return asyncFileSink == null ? 0L : asyncFileSink.droppedCount();
    }

    public long failedFileEvents() {
        return asyncFileSink == null ? 0L : asyncFileSink.failureCount();
    }

    @Override
    public void close() {
        if (asyncFileSink != null) {
            asyncFileSink.close();
        }
        if (fileSink != null) {
            fileSink.close();
        }
    }

    private static FlowerObservationSink sanitized(FlowerObservationSink destination) {
        return new SanitizingFlowerObservationSink(
                destination,
                FlowerObservationSanitizers.removeAttributes(
                        "authorization",
                        "api.key",
                        "apiKey",
                        "user.email",
                        "customer.id",
                        "customerId"));
    }
}

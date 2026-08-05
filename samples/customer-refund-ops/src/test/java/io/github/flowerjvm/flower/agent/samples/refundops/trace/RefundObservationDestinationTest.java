package io.github.flowerjvm.flower.agent.samples.refundops.trace;

import io.github.flowerjvm.flower.agent.samples.refundops.config.ObservationProperties;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationEvent;
import io.github.flowerjvm.flower.observability.tracing.InMemoryFlowerObservationSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefundObservationDestinationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void sanitizesMemoryAndAsyncJsonLinesDestinations() throws Exception {
        Path file = tempDirectory.resolve("observations.jsonl");
        InMemoryFlowerObservationSink memory = new InMemoryFlowerObservationSink();
        RefundObservationDestination destination = new RefundObservationDestination(
                memory,
                new ObservationProperties(file.toString(), 16));

        try (destination) {
            destination.publish(FlowerObservationEvent
                    .builder("flower-agent", "MODEL_CALL_COMPLETED")
                    .eventId("event-1")
                    .traceId("trace-1")
                    .runId("run-1")
                    .sequence(1L)
                    .occurredAt(Instant.parse("2026-08-05T00:00:00Z"))
                    .attributes(Map.of(
                            "customerId", "customer-lee",
                            "status", "SUCCEEDED"))
                    .build());
        }

        assertThat(memory.snapshot()).singleElement().satisfies(event -> {
            assertThat(event.attributes()).containsEntry("status", "SUCCEEDED");
            assertThat(event.attributes()).doesNotContainKey("customerId");
        });
        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8))
                .singleElement()
                .asString()
                .contains("\"traceId\":\"trace-1\"")
                .contains("\"status\":\"SUCCEEDED\"")
                .doesNotContain("customerId")
                .doesNotContain("customer-lee");
        assertThat(destination.droppedFileEvents()).isZero();
        assertThat(destination.failedFileEvents()).isZero();
    }
}

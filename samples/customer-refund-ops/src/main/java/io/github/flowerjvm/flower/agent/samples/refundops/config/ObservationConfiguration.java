package io.github.flowerjvm.flower.agent.samples.refundops.config;

import io.github.flowerjvm.flower.action.runtime.audit.TraceSink;
import io.github.flowerjvm.flower.action.runtime.observability.ActionRuntimeObservationCorrelation;
import io.github.flowerjvm.flower.action.runtime.observability.ActionRuntimeObservationTraceSink;
import io.github.flowerjvm.flower.agent.observability.AgentObservationCorrelation;
import io.github.flowerjvm.flower.agent.observability.AgentObservationSinkAdapter;
import io.github.flowerjvm.flower.agent.observation.AgentEventSink;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.TraceCorrelationRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.RefundObservationDestination;
import io.github.flowerjvm.flower.ai.harness.observability.AiHarnessObservationCorrelation;
import io.github.flowerjvm.flower.ai.harness.observability.AiHarnessObservationTraceListener;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationEvent;
import io.github.flowerjvm.flower.observability.tracing.FlowerObservationSink;
import io.github.flowerjvm.flower.observability.tracing.FlowerTraceObservationSink;
import io.github.flowerjvm.flower.observability.tracing.FlowerTraceSinkListener;
import io.github.flowerjvm.flower.observability.tracing.InMemoryFlowerObservationSink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;

@Configuration
public class ObservationConfiguration {

    @Bean
    TraceCorrelationRegistry traceCorrelationRegistry() {
        return new TraceCorrelationRegistry();
    }

    @Bean
    InMemoryFlowerObservationSink flowerObservationStore() {
        return new InMemoryFlowerObservationSink();
    }

    @Bean(name = "refundObservationDestination", destroyMethod = "close")
    RefundObservationDestination refundObservationDestination(
            InMemoryFlowerObservationSink memory,
            ObservationProperties properties
    ) {
        return new RefundObservationDestination(memory, properties);
    }

    @Bean
    FlowerTraceSinkListener flowerTraceListener(
            @Qualifier("refundObservationDestination") FlowerObservationSink destination,
            TraceCorrelationRegistry correlations
    ) {
        FlowerObservationSink correlatedCoreDestination = event ->
                destination.publish(withHostCorrelation(event, correlations));
        return new FlowerTraceSinkListener(new FlowerTraceObservationSink(correlatedCoreDestination));
    }

    @Bean
    AgentEventSink agentEventSink(
            @Qualifier("refundObservationDestination") FlowerObservationSink destination,
            TraceCorrelationRegistry correlations
    ) {
        return new AgentObservationSinkAdapter(destination, event -> correlations.find(event.runId())
                .map(value -> new AgentObservationCorrelation(value.traceId(), value.parentRunId()))
                .orElseGet(() -> AgentObservationCorrelation.standalone(event.runId())));
    }

    @Bean
    AiHarnessObservationTraceListener aiHarnessObservationTraceListener(
            @Qualifier("refundObservationDestination") FlowerObservationSink destination,
            TraceCorrelationRegistry correlations,
            Clock sampleClock
    ) {
        return new AiHarnessObservationTraceListener(destination, context -> {
            String runId = context.runId().value();
            return correlations.find(runId)
                    .map(value -> new AiHarnessObservationCorrelation(value.traceId(), value.parentRunId()))
                    .orElseGet(() -> AiHarnessObservationCorrelation.standalone(runId));
        }, sampleClock);
    }

    @Bean(name = "refundActionTraceSink")
    TraceSink refundActionTraceSink(
            @Qualifier("refundObservationDestination") FlowerObservationSink destination,
            TraceCorrelationRegistry correlations
    ) {
        return new ActionRuntimeObservationTraceSink(destination, event -> correlations.find(event.runId())
                .map(value -> new ActionRuntimeObservationCorrelation(
                        value.traceId(), event.runId(), value.parentRunId()))
                .orElseGet(() -> new ActionRuntimeObservationCorrelation(
                        event.runId(), event.runId(), null)));
    }

    private static FlowerObservationEvent withHostCorrelation(
            FlowerObservationEvent event,
            TraceCorrelationRegistry correlations
    ) {
        TraceCorrelationRegistry.Correlation correlation = correlations.find(event.runId())
                .or(() -> correlations.find(String.valueOf(
                        event.attributes().getOrDefault(FlowerTraceObservationSink.FLOW_KEY, ""))))
                .orElse(null);
        if (correlation == null || correlation.traceId().equals(event.traceId())) {
            return event;
        }
        return event.toBuilder().traceId(correlation.traceId()).build();
    }
}

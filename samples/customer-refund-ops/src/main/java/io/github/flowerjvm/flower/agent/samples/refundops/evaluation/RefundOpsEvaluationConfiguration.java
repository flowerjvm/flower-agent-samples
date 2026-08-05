package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.agent.gateway.AgentModelGateway;
import io.github.flowerjvm.flower.observability.awaiter.FlowAwaiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "sample.evaluation",
        name = "enabled",
        havingValue = "true")
public class RefundOpsEvaluationConfiguration {

    @Bean
    FlowAwaiter refundEvaluationFlowAwaiter() {
        return FlowAwaiter.create();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "sample.evaluation",
            name = "scripted-model",
            havingValue = "true",
            matchIfMissing = true)
    AgentModelGateway refundEvaluationModelGateway(Clock sampleClock) {
        return new RefundOpsEvaluationScriptedModelGateway(sampleClock);
    }
}

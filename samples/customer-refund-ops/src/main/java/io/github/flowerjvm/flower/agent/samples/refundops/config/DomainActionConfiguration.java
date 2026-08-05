package io.github.flowerjvm.flower.agent.samples.refundops.config;

import io.github.flowerjvm.flower.action.runtime.DefaultActionRuntime;
import io.github.flowerjvm.flower.action.runtime.action.ActionRegistry;
import io.github.flowerjvm.flower.action.runtime.action.InMemoryActionRegistry;
import io.github.flowerjvm.flower.action.runtime.approval.ApprovalGate;
import io.github.flowerjvm.flower.action.runtime.audit.TraceSink;
import io.github.flowerjvm.flower.action.runtime.duplicate.InMemoryDuplicateActionPolicy;
import io.github.flowerjvm.flower.action.runtime.guard.PreExecutionDecision;
import io.github.flowerjvm.flower.action.runtime.guard.PreExecutionGuard;
import io.github.flowerjvm.flower.action.runtime.run.InMemoryRunStore;
import io.github.flowerjvm.flower.action.runtime.run.RunStore;
import io.github.flowerjvm.flower.agent.samples.refundops.action.IssueRefundAction;
import io.github.flowerjvm.flower.agent.samples.refundops.action.IssueRefundInputValidator;
import io.github.flowerjvm.flower.agent.samples.refundops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.refundops.action.RefundPolicyGate;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.RefundEligibility;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
public class DomainActionConfiguration {

    @Bean
    Clock sampleClock() {
        return Clock.systemUTC();
    }

    @Bean
    OrderStore orderStore(Clock sampleClock) {
        return new OrderStore(sampleClock);
    }

    @Bean
    IssueRefundAction issueRefundAction(OrderStore orders) {
        return new IssueRefundAction(orders);
    }

    @Bean
    ActionRegistry refundActionRegistry(IssueRefundAction refundAction) {
        return new InMemoryActionRegistry(List.of(refundAction));
    }

    @Bean
    RecordingAuditSink refundAuditSink() {
        return new RecordingAuditSink();
    }

    @Bean
    RunStore refundActionRunStore() {
        return new InMemoryRunStore();
    }

    @Bean
    DefaultActionRuntime refundActionRuntime(
            ActionRegistry refundActionRegistry,
            RecordingAuditSink refundAuditSink,
            RunStore refundActionRunStore,
            @Qualifier("refundActionTraceSink") TraceSink traceSink,
            OrderStore orders
    ) {
        PreExecutionGuard guard = (proposal, definition, context, policy) -> {
            String orderId = String.valueOf(proposal.input().get("orderId"));
            RefundEligibility eligibility = orders.eligibility(orderId);
            long requested = proposal.input().get("amount") instanceof Number number
                    ? number.longValue() : -1L;
            if (!eligibility.eligible()) {
                return PreExecutionDecision.deny(eligibility.code(), eligibility.reason());
            }
            return requested == eligibility.refundableAmount()
                    ? PreExecutionDecision.allow()
                    : PreExecutionDecision.deny(
                            "REFUND_AMOUNT_CHANGED",
                            "The requested amount no longer matches the refundable amount.");
        };
        return new DefaultActionRuntime(
                refundActionRegistry,
                new IssueRefundInputValidator(orders),
                new RefundPolicyGate(),
                ApprovalGate.unsupported(),
                new InMemoryDuplicateActionPolicy(),
                refundAuditSink,
                traceSink,
                refundActionRunStore,
                guard);
    }
}

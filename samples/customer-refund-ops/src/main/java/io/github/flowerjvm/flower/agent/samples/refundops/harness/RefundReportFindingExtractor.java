package io.github.flowerjvm.flower.agent.samples.refundops.harness;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.finding.AiFindingSeverity;
import io.github.flowerjvm.flower.ai.harness.finding.FindingExtractor;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.ArrayList;
import java.util.List;

public final class RefundReportFindingExtractor implements FindingExtractor<RefundReport> {

    @Override
    public List<AiFinding> extract(RefundReport value, AiHarnessRunContext context) {
        List<AiFinding> findings = new ArrayList<>();
        if (value.outcome() == RefundOutcome.MANUAL_REVIEW) {
            findings.add(AiFinding.of(
                    "MANUAL_REVIEW_REQUIRED",
                    AiFindingSeverity.HIGH,
                    "The refund requires a human decision.").withLocation(value.orderId()));
        }
        value.residualRisks().stream()
                .map(risk -> AiFinding.of("RESIDUAL_RISK", AiFindingSeverity.MEDIUM, risk)
                        .withLocation(value.orderId()))
                .forEach(findings::add);
        return List.copyOf(findings);
    }
}

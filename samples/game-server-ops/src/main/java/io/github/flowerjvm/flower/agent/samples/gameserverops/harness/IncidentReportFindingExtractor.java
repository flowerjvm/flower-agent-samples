package io.github.flowerjvm.flower.agent.samples.gameserverops.harness;

import io.github.flowerjvm.flower.ai.harness.finding.AiFinding;
import io.github.flowerjvm.flower.ai.harness.finding.AiFindingSeverity;
import io.github.flowerjvm.flower.ai.harness.finding.FindingExtractor;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

public final class IncidentReportFindingExtractor implements FindingExtractor<IncidentReport> {

    @Override
    public List<AiFinding> extract(IncidentReport value, AiHarnessRunContext context) {
        return value.residualRisks().stream()
                .map(risk -> AiFinding.of("RESIDUAL_RISK", AiFindingSeverity.MEDIUM, risk)
                        .withLocation(value.serverId()))
                .toList();
    }
}

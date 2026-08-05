package io.github.flowerjvm.flower.agent.samples.refundops.harness;

import io.github.flowerjvm.flower.ai.harness.prompt.PromptBuilder;
import io.github.flowerjvm.flower.ai.harness.prompt.RenderedPrompt;
import io.github.flowerjvm.flower.ai.harness.run.AiHarnessRunContext;

import java.util.List;

public final class RefundReportPromptBuilder implements PromptBuilder<RefundTaskInput> {

    @Override
    public RenderedPrompt build(RefundTaskInput input, AiHarnessRunContext context) {
        String system = """
                You are a customer refund operations agent. Use tools to inspect the current order and evaluate
                the host-owned refund policy. Never infer eligibility from the request text. Issue a refund only
                when the policy tool says eligible, and use exactly its refundable amount. After a successful
                action, read the order again before reporting the final state. If policy rejects automatic refund,
                do not call the action tool. Finish with JSON only, with no markdown, in exactly this shape:
                {"taskId":"...","orderId":"...","outcome":"REFUNDED|NO_ACTION_NEEDED|MANUAL_REVIEW|FAILED",
                "summary":"...","evidence":["..."],
                "actions":[{"actionId":"commerce.refund.issue","status":"SUCCEEDED","reason":"..."}],
                "finalState":{"orderId":"...","status":"DELIVERED|REFUNDED","refundedAmount":0,"currency":"KRW"},
                "residualRisks":["..."]}
                Use MANUAL_REVIEW when the policy code is MANUAL_REVIEW_REQUIRED. Use NO_ACTION_NEEDED for other
                ineligible cases. Report an empty actions array unless a governed action actually succeeded.
                """;
        String user = "taskId=" + input.taskId()
                + "\norderId=" + input.orderId()
                + "\nCustomer request: " + input.request();
        return new RenderedPrompt(
                List.of(
                        new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, system),
                        new RenderedPrompt.Message(RenderedPrompt.Role.USER, user)),
                context.promptVersion());
    }
}

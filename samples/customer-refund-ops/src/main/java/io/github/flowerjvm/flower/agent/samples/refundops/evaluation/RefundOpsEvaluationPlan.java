package io.github.flowerjvm.flower.agent.samples.refundops.evaluation;

import io.github.flowerjvm.flower.evaluation.EvaluationDataset;
import io.github.flowerjvm.flower.evaluation.EvaluationEvaluators;
import io.github.flowerjvm.flower.evaluation.EvaluationExample;
import io.github.flowerjvm.flower.evaluation.EvaluationMetrics;
import io.github.flowerjvm.flower.evaluation.EvaluationSuite;

/** Fixed refund scenarios and deterministic business criteria. */
public final class RefundOpsEvaluationPlan {

    public static final String DATASET_ID = "customer-refund-ops-scenarios";
    public static final String DATASET_VERSION = "v1";

    private RefundOpsEvaluationPlan() {
    }

    public static EvaluationDataset dataset() {
        return EvaluationDataset.builder(DATASET_ID, DATASET_VERSION)
                .name("Customer refund operations scenarios")
                .example(eligibleRefund())
                .example(expiredRefundWindow())
                .example(highValueManualReview())
                .build();
    }

    public static EvaluationExample eligibleRefund() {
        return EvaluationExample.builder("eligible-refund")
                .input("orderId", "order-1001")
                .input("request", "Refund this delivered order if the current policy allows it.")
                .expected("outcome", "REFUNDED")
                .expected("domainStatus", "REFUNDED")
                .expected("refundExecutionCount", 1)
                .expected("actionObserved", true)
                .expected("allActionAttemptsSucceeded", true)
                .expected("manualReviewRequired", false)
                .expected("reportMatchesDomain", true)
                .tag("eligible")
                .tag("action")
                .build();
    }

    public static EvaluationExample expiredRefundWindow() {
        return EvaluationExample.builder("expired-refund-window")
                .input("orderId", "order-1002")
                .input("request", "Please refund this order if it is eligible.")
                .expected("outcome", "NO_ACTION_NEEDED")
                .expected("domainStatus", "DELIVERED")
                .expected("refundExecutionCount", 0)
                .expected("actionObserved", false)
                .expected("allActionAttemptsSucceeded", true)
                .expected("manualReviewRequired", false)
                .expected("reportMatchesDomain", true)
                .tag("ineligible")
                .tag("no-action")
                .build();
    }

    public static EvaluationExample highValueManualReview() {
        return EvaluationExample.builder("high-value-manual-review")
                .input("orderId", "order-1003")
                .input("request", "Refund this high-value order.")
                .expected("outcome", "MANUAL_REVIEW")
                .expected("domainStatus", "DELIVERED")
                .expected("refundExecutionCount", 0)
                .expected("actionObserved", false)
                .expected("allActionAttemptsSucceeded", true)
                .expected("manualReviewRequired", true)
                .expected("reportMatchesDomain", true)
                .tag("manual-review")
                .tag("safety")
                .build();
    }

    public static EvaluationSuite suite() {
        return EvaluationSuite.builder()
                .evaluator(EvaluationEvaluators.expectedEquals("outcome"))
                .evaluator(EvaluationEvaluators.expectedEquals("domainStatus"))
                .evaluator(EvaluationEvaluators.expectedEquals("refundExecutionCount"))
                .evaluator(EvaluationEvaluators.expectedEquals("actionObserved"))
                .evaluator(EvaluationEvaluators.expectedEquals("allActionAttemptsSucceeded"))
                .evaluator(EvaluationEvaluators.expectedEquals("manualReviewRequired"))
                .evaluator(EvaluationEvaluators.expectedEquals("reportMatchesDomain"))
                .evaluator(EvaluationEvaluators.minimumActualCollectionSize("evidence", 1))
                .evaluator(EvaluationEvaluators.optional(
                        EvaluationEvaluators.metricAtMost(EvaluationMetrics.TOOL_CALLS, 8)))
                .evaluator(EvaluationEvaluators.optional(
                        EvaluationEvaluators.metricAtMost(EvaluationMetrics.TURNS, 10)))
                .build();
    }
}

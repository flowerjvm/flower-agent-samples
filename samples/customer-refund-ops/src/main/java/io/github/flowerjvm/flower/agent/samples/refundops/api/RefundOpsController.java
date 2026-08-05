package io.github.flowerjvm.flower.agent.samples.refundops.api;

import io.github.flowerjvm.flower.agent.samples.refundops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.refundops.config.ModelProperties;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderSnapshot;
import io.github.flowerjvm.flower.agent.samples.refundops.domain.OrderStore;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTask;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.refundops.task.RefundTaskService;
import io.github.flowerjvm.flower.agent.samples.refundops.trace.TraceCorrelationRegistry;
import io.github.flowerjvm.flower.observability.tracing.InMemoryFlowerObservationSink;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RefundOpsController {

    private final OrderStore orders;
    private final RefundTaskService taskService;
    private final RefundTaskRegistry tasks;
    private final RecordingAuditSink auditSink;
    private final InMemoryFlowerObservationSink observations;
    private final TraceCorrelationRegistry correlations;
    private final ModelProperties modelProperties;

    public RefundOpsController(
            OrderStore orders,
            RefundTaskService taskService,
            RefundTaskRegistry tasks,
            RecordingAuditSink auditSink,
            InMemoryFlowerObservationSink observations,
            TraceCorrelationRegistry correlations,
            ModelProperties modelProperties
    ) {
        this.orders = orders;
        this.taskService = taskService;
        this.tasks = tasks;
        this.auditSink = auditSink;
        this.observations = observations;
        this.correlations = correlations;
        this.modelProperties = modelProperties;
    }

    @GetMapping("/config")
    ConfigView config() {
        URI endpoint = URI.create(modelProperties.baseUrl());
        return new ConfigView(
                endpoint.getScheme() + "://" + endpoint.getAuthority(),
                modelProperties.model(),
                modelProperties.credentialConfigured());
    }

    @GetMapping("/orders")
    List<OrderSnapshot> orders() {
        return orders.orders();
    }

    @PostMapping("/refund-tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RefundTaskView start(@RequestBody StartRefundTaskRequest request) {
        return view(taskService.start(request.orderId(), request.message()));
    }

    @GetMapping("/refund-tasks/{taskId}")
    RefundTaskView task(@PathVariable String taskId) {
        return view(tasks.require(taskId));
    }

    @GetMapping("/refund-tasks/{taskId}/trace")
    List<ObservationView> trace(@PathVariable String taskId) {
        tasks.require(taskId);
        return observations.snapshot().stream()
                .filter(event -> taskId.equals(event.traceId()))
                .map(ObservationView::from)
                .toList();
    }

    @PostMapping("/refund-tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RefundTaskView cancel(@PathVariable String taskId) {
        taskService.cancel(taskId);
        return view(tasks.require(taskId));
    }

    @PostMapping("/demo/reset")
    List<OrderSnapshot> reset() {
        boolean active = tasks.all().stream()
                .map(RefundTask::harnessFlow)
                .filter(java.util.Objects::nonNull)
                .anyMatch(flow -> !flow.flow().state().isTerminal());
        if (active) {
            throw new IllegalStateException("finish or cancel active tasks before resetting the demo");
        }
        orders.reset();
        auditSink.clear();
        observations.clear();
        correlations.clear();
        tasks.clear();
        return orders.orders();
    }

    private RefundTaskView view(RefundTask task) {
        return RefundTaskView.from(task, orders, auditSink, observations);
    }

    public record StartRefundTaskRequest(String orderId, String message) {
    }

    public record ConfigView(String endpoint, String model, boolean credentialConfigured) {
    }
}

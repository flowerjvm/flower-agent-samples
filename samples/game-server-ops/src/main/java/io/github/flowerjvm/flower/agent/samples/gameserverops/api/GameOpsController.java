package io.github.flowerjvm.flower.agent.samples.gameserverops.api;

import io.github.flowerjvm.flower.agent.samples.gameserverops.action.RecordingAuditSink;
import io.github.flowerjvm.flower.agent.samples.gameserverops.config.ModelProperties;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerFleet;
import io.github.flowerjvm.flower.agent.samples.gameserverops.domain.GameServerSnapshot;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTask;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskRegistry;
import io.github.flowerjvm.flower.agent.samples.gameserverops.task.GameOpsTaskService;
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
public class GameOpsController {

    private final GameServerFleet fleet;
    private final GameOpsTaskService taskService;
    private final GameOpsTaskRegistry taskRegistry;
    private final RecordingAuditSink auditSink;
    private final ModelProperties modelProperties;

    public GameOpsController(
            GameServerFleet fleet,
            GameOpsTaskService taskService,
            GameOpsTaskRegistry taskRegistry,
            RecordingAuditSink auditSink,
            ModelProperties modelProperties
    ) {
        this.fleet = fleet;
        this.taskService = taskService;
        this.taskRegistry = taskRegistry;
        this.auditSink = auditSink;
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

    @GetMapping("/servers")
    List<GameServerSnapshot> servers() {
        return fleet.servers();
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GameOpsTaskView start(@RequestBody StartTaskRequest request) {
        return view(taskService.start(request.message()));
    }

    @GetMapping("/tasks/{taskId}")
    GameOpsTaskView task(@PathVariable String taskId) {
        return view(taskRegistry.require(taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GameOpsTaskView cancel(@PathVariable String taskId) {
        taskService.cancel(taskId);
        return view(taskRegistry.require(taskId));
    }

    @PostMapping("/demo/reset")
    List<GameServerSnapshot> reset() {
        boolean active = taskRegistry.all().stream()
                .map(GameOpsTask::harnessFlow)
                .filter(java.util.Objects::nonNull)
                .anyMatch(flow -> !flow.flow().state().isTerminal());
        if (active) {
            throw new IllegalStateException("finish or cancel the active task before resetting the demo");
        }
        fleet.reset();
        auditSink.clear();
        taskRegistry.clear();
        return fleet.servers();
    }

    private GameOpsTaskView view(GameOpsTask task) {
        return GameOpsTaskView.from(task, auditSink);
    }

    public record StartTaskRequest(String message) {
    }

    public record ConfigView(String endpoint, String model, boolean credentialConfigured) {
    }
}

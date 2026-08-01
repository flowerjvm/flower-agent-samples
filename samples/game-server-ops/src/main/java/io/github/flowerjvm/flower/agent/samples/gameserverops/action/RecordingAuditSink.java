package io.github.flowerjvm.flower.agent.samples.gameserverops.action;

import io.github.flowerjvm.flower.action.runtime.audit.AuditEvent;
import io.github.flowerjvm.flower.action.runtime.audit.AuditSink;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingAuditSink implements AuditSink {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> eventsForRuns(List<String> runIds) {
        return events.stream()
                .filter(event -> runIds.contains(event.runId()))
                .toList();
    }

    public void clear() {
        events.clear();
    }
}

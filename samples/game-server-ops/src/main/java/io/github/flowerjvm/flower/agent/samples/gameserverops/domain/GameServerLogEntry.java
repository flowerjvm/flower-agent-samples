package io.github.flowerjvm.flower.agent.samples.gameserverops.domain;

import java.time.Instant;

public record GameServerLogEntry(
        Instant occurredAt,
        String level,
        String message
) {
}

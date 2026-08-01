package io.github.flowerjvm.flower.agent.samples.gameserverops.domain;

import java.time.Instant;

public record GameServerSnapshot(
        String serverId,
        String region,
        GameServerState state,
        int activePlayers,
        int restartCount,
        Instant updatedAt
) {
}

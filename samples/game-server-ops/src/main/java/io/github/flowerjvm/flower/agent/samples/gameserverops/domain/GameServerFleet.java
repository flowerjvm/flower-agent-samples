package io.github.flowerjvm.flower.agent.samples.gameserverops.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GameServerFleet {

    private final Clock clock;
    private final Map<String, MutableServer> servers = new LinkedHashMap<>();

    public GameServerFleet(Clock clock) {
        this.clock = clock;
        reset();
    }

    public synchronized List<GameServerSnapshot> servers() {
        return servers.values().stream()
                .map(MutableServer::snapshot)
                .sorted(Comparator.comparing(GameServerSnapshot::serverId))
                .toList();
    }

    public synchronized GameServerSnapshot server(String serverId) {
        return requireServer(serverId).snapshot();
    }

    public synchronized boolean contains(String serverId) {
        return serverId != null && servers.containsKey(serverId.trim());
    }

    public synchronized List<GameServerLogEntry> searchLogs(String serverId, String query, int limit) {
        MutableServer server = requireServer(serverId);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return server.logs.stream()
                .filter(entry -> normalized.isEmpty()
                        || entry.level().toLowerCase(Locale.ROOT).contains(normalized)
                        || entry.message().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(GameServerLogEntry::occurredAt).reversed())
                .limit(boundedLimit)
                .toList();
    }

    public synchronized GameServerSnapshot restart(String serverId, String reason) {
        MutableServer server = requireServer(serverId);
        if (server.state != GameServerState.DEGRADED) {
            throw new IllegalStateException("server is not degraded: " + serverId);
        }
        Instant now = clock.instant();
        server.restartCount++;
        server.state = GameServerState.HEALTHY;
        server.activePlayers = Math.max(0, server.activePlayers - 3);
        server.updatedAt = now;
        server.logs.add(new GameServerLogEntry(
                now,
                "INFO",
                "Controlled restart completed. reason=" + normalizeReason(reason)));
        server.logs.add(new GameServerLogEntry(
                now,
                "INFO",
                "Matchmaking and session health checks passed."));
        return server.snapshot();
    }

    public synchronized void reset() {
        Instant now = clock.instant();
        servers.clear();
        servers.put("server-alpha", new MutableServer(
                "server-alpha",
                "kr-seoul-1",
                GameServerState.DEGRADED,
                127,
                0,
                now,
                List.of(
                        new GameServerLogEntry(now.minusSeconds(90), "ERROR",
                                "Match persistence failed: database connection pool exhausted."),
                        new GameServerLogEntry(now.minusSeconds(55), "WARN",
                                "Session write latency exceeded 2200ms for 18 players."),
                        new GameServerLogEntry(now.minusSeconds(20), "ERROR",
                                "Health probe failed three consecutive times."))));
        servers.put("server-beta", new MutableServer(
                "server-beta",
                "kr-seoul-1",
                GameServerState.HEALTHY,
                84,
                0,
                now,
                List.of(
                        new GameServerLogEntry(now.minusSeconds(60), "INFO",
                                "Matchmaking queue depth is within normal range."),
                        new GameServerLogEntry(now.minusSeconds(15), "INFO",
                                "Health probe passed."))));
    }

    private MutableServer requireServer(String serverId) {
        String normalized = serverId == null ? "" : serverId.trim();
        MutableServer server = servers.get(normalized);
        if (server == null) {
            throw new IllegalArgumentException("unknown server: " + normalized);
        }
        return server;
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "agent-requested recovery" : reason.trim();
    }

    private static final class MutableServer {
        private final String serverId;
        private final String region;
        private GameServerState state;
        private int activePlayers;
        private int restartCount;
        private Instant updatedAt;
        private final List<GameServerLogEntry> logs;

        private MutableServer(
                String serverId,
                String region,
                GameServerState state,
                int activePlayers,
                int restartCount,
                Instant updatedAt,
                List<GameServerLogEntry> logs
        ) {
            this.serverId = serverId;
            this.region = region;
            this.state = state;
            this.activePlayers = activePlayers;
            this.restartCount = restartCount;
            this.updatedAt = updatedAt;
            this.logs = new ArrayList<>(logs);
        }

        private GameServerSnapshot snapshot() {
            return new GameServerSnapshot(
                    serverId,
                    region,
                    state,
                    activePlayers,
                    restartCount,
                    updatedAt);
        }
    }
}

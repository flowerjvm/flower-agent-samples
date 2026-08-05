package io.github.flowerjvm.flower.agent.samples.refundops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@ConfigurationProperties("sample.observation")
public record ObservationProperties(
        String file,
        int queueCapacity
) {

    public ObservationProperties {
        file = file == null ? "" : file.trim();
        queueCapacity = queueCapacity <= 0 ? 4096 : queueCapacity;
    }

    public Optional<Path> filePath() {
        if (file.isBlank() || "none".equalsIgnoreCase(file)) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(file).toAbsolutePath().normalize());
    }
}

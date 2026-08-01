package io.github.flowerjvm.flower.agent.samples.gameserverops.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModelCredentialResolver {

    private ModelCredentialResolver() {
    }

    public static String resolve(ModelProperties properties) {
        if (!properties.apiKey().isBlank()) {
            return properties.apiKey();
        }
        if (properties.apiKeyFile().isBlank()) {
            return "";
        }
        Path path = Path.of(properties.apiKeyFile()).toAbsolutePath().normalize();
        try {
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (value.isBlank()) {
                throw new IllegalStateException("model API key file is empty: " + path);
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read model API key file: " + path, exception);
        }
    }
}

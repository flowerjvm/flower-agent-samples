package io.github.flowerjvm.flower.agent.samples.refundops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("sample.model")
public record ModelProperties(
        String baseUrl,
        String model,
        String apiKey,
        String apiKeyFile,
        Duration timeout
) {

    public ModelProperties {
        baseUrl = textOrDefault(baseUrl, "https://api.openai.com/v1");
        model = textOrDefault(model, "gpt-4.1-mini");
        apiKey = apiKey == null ? "" : apiKey.trim();
        apiKeyFile = apiKeyFile == null ? "" : apiKeyFile.trim();
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("sample.model.timeout must be positive");
        }
    }

    public boolean credentialConfigured() {
        return !apiKey.isBlank() || !apiKeyFile.isBlank();
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

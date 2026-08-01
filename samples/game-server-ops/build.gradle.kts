plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.github.flowerjvm.flower-check")
}

description = "Web sample composing Flower, Flower Agent, AI Harness, and Action Runtime."

val flowerAgentVersion = providers.gradleProperty("flowerAgentVersion").orElse("0.1.0")

dependencies {
    implementation("io.github.flowerjvm:flower-spring-boot-starter:0.1.1")
    implementation("io.github.flowerjvm:flower-observability:0.1.1")

    implementation("io.github.flowerjvm:flower-agent-core:${flowerAgentVersion.get()}")
    implementation("io.github.flowerjvm:flower-agent-model-openai-compatible:${flowerAgentVersion.get()}")

    implementation("io.github.flowerjvm:flower-ai-harness-core:0.1.2")
    implementation("io.github.flowerjvm:flower-ai-harness-validator-jackson:0.1.2")
    implementation("io.github.flowerjvm:flower-action-runtime-core:0.3.1")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("io.github.flowerjvm:flower-check-annotations:0.1.1")

    testImplementation("io.github.flowerjvm:flower-testkit:0.1.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

flowerCheck {
    includeTests.set(false)
}

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.github.flowerjvm.flower-check")
}

description = "End-to-end refund operations agent with common Flower observations."

dependencies {
    implementation("io.github.flowerjvm:flower-spring-boot-starter:0.1.2-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-observability:0.1.2-SNAPSHOT")

    implementation("io.github.flowerjvm:flower-agent-core:0.2.0-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-agent-recipes:0.2.0-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-agent-observability:0.2.0-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-agent-model-openai-compatible:0.2.0-SNAPSHOT")

    implementation("io.github.flowerjvm:flower-ai-harness-core:0.1.3-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-ai-harness-observability:0.1.3-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-ai-harness-validator-jackson:0.1.3-SNAPSHOT")

    implementation("io.github.flowerjvm:flower-action-runtime-core:0.3.2-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-action-runtime-observability:0.3.2-SNAPSHOT")
    implementation("io.github.flowerjvm:flower-evaluation:0.1.2-SNAPSHOT")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("io.github.flowerjvm:flower-check-annotations:0.1.2-SNAPSHOT")

    testImplementation("io.github.flowerjvm:flower-testkit:0.1.2-SNAPSHOT")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

flowerCheck {
    includeTests.set(false)
}

tasks.register<JavaExec>("runEvaluation") {
    group = "application"
    description = "Runs the refund Agent evaluation and writes linked Studio JSONL files."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(
        "io.github.flowerjvm.flower.agent.samples.refundops.evaluation.RefundOpsEvaluationMain"
    )
    workingDir = project.projectDir
}

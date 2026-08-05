plugins {
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("io.github.flowerjvm.flower-check") version "0.1.1" apply false
}

allprojects {
    group = "io.github.flowerjvm.flower.agent.samples"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenLocal {
            content {
                includeVersionByRegex("io.github.flowerjvm", ".*", ".*-SNAPSHOT")
            }
        }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    the<JavaPluginExtension>().toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

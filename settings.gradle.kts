pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "flower-agent-samples"

include("samples:game-server-ops")
include("samples:customer-refund-ops")

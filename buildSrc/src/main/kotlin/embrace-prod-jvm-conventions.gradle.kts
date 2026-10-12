import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    id("embrace-jvm-conventions")
    id("embrace-publishing-conventions")
}

// Kover is only applied on CI by the root project
pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
    configure<KoverProjectExtension> {
        currentProject {
            instrumentation {
                // Kover's agent slows tests - only attach when coverage is requested
                disabledForAll.set(!providers.gradleProperty("embrace.coverage").isPresent)
            }
        }
    }
}

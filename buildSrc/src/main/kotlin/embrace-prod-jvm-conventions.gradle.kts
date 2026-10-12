plugins {
    id("embrace-jvm-conventions")
    id("embrace-publishing-conventions")
}

kover {
    currentProject {
        instrumentation {
            // Kover's agent slows tests - only attach when coverage is requested
            disabledForAll.set(!providers.gradleProperty("embrace.coverage").isPresent)
        }
    }
}

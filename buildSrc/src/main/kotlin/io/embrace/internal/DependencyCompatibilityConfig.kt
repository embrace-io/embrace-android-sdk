package io.embrace.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Registers both API and runtime dependency checks to ensure compatibility
 * with the minimum supported Kotlin version defined in the version catalog.
 */
fun Project.configureDependencyChecks() {
    val minSupportedKotlinVersion = findVersion("minSupportedKotlinVersion")
    registerCheckApiDependencies(kotlinVersion = minSupportedKotlinVersion)
    registerCheckRuntimeDependencies(kotlinVersion = minSupportedKotlinVersion)
}

/**
 * Registers a task to check API dependencies for incompatible Kotlin metadata.
 *
 * @param kotlinVersion The Kotlin version to check against (e.g., "2.0.21")
 */
fun Project.registerCheckApiDependencies(
    kotlinVersion: String,
) {

    // Create a custom resolvable configuration that extends from apiElements.
    // apiElements itself is not resolvable, so we need this to download and inspect jar files.
    val checkConfig = configurations.register("embraceCompatCheck") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    configurations
        .withType(Configuration::class.java)
        .matching { it.name == "apiElements" }
        .configureEach { checkConfig.get().extendsFrom(this) }

    val checkTask = tasks.register("checkApiDependencies", CheckApiDependenciesTask::class.java)
    checkTask.configure {
        this.kotlinVersion.set(kotlinVersion)
        compileClasspath.from(checkConfig)
        projectPath.set(project.path)
        outputFile.set(layout.buildDirectory.file("embrace-compat/api-dependencies-check.txt").map { it.asFile })
    }

    tasks.named("check") {
        dependsOn(checkTask)
    }
}

/**
 * Registers a task to check runtime dependencies for incompatible kotlin-stdlib versions.
 * Only checks published/production dependencies, skips test compilations.
 *
 * @param kotlinVersion The Kotlin version to check against (e.g., "2.0.21")
 */
fun Project.registerCheckRuntimeDependencies(
    kotlinVersion: String,
) {
    val kotlin = extensions.findByName("kotlin")

    val compilations = when (kotlin) {
        is KotlinJvmProjectExtension -> kotlin.target.compilations
        is KotlinAndroidProjectExtension -> kotlin.target.compilations
        else -> return
    }

    // Create a single anchor task that will run all the runtime dependency checks, so we can run ./gradlew checkRuntimeDependencies
    val lifecycleTask = tasks.register("checkRuntimeDependencies")

    compilations.matching { it.name == "main" || it.name == "release" }.all {
        val configurationName = runtimeDependencyConfigurationName
        val configuration = project.configurations.named(configurationName)

        val stdlibVersionsProvider = configuration.map { config ->
            config.incoming.resolutionResult.allComponents
                .mapNotNull { it.id as? ModuleComponentIdentifier }
                .filter { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib" }
                .map { it.version }
                .distinct()
        }

        val taskName = "check${name.replaceFirstChar { it.uppercase() }}RuntimeDependencies"
        val checkTask = project.tasks.register(taskName, CheckRuntimeDependenciesTask::class.java)
        checkTask.configure {
            this.kotlinVersion.set(kotlinVersion)
            transitiveKotlinStdlibVersions.set(stdlibVersionsProvider)
            projectPath.set(project.path)
            this.configurationName.set(configurationName)
            outputFile.set(
                project.layout.buildDirectory.file("embrace-compat/runtime-dependencies-${name}.txt").map { it.asFile }
            )
        }

        // Run tasks on ./gradlew check
        project.tasks.named("check") {
            dependsOn(checkTask)
        }

        lifecycleTask.configure {
            dependsOn(checkTask)
        }
    }
}

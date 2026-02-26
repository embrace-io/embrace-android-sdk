import kotlinx.validation.KotlinApiBuildTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("embrace-prod-android-conventions")
    id("binary-compatibility-validator")
    id("org.jetbrains.dokka")
}

kotlin.explicitApi()

// Workaround for https://youtrack.jetbrains.com/issue/KT-83410 and
// https://issuetracker.google.com/issues/470109449.
//
// 1. AGP 9 uses KotlinBaseApiPlugin (not KotlinBasePluginWrapper) which does not populate
//    KotlinJvmAndroidCompilation.output.classesDirs. BCV therefore creates the apiBuild task
//    (triggered by our kotlin-android stub in embrace-android-conventions) but leaves its
//    inputClassesDirs empty, causing the task to fail. Wire the release compilation output
//    directory manually so BCV has the class files it needs.
//
// 2. BCV's withKotlinPluginVersion adds kotlin-metadata-jvm to the worker classpath only for
//    kotlin-jvm and kotlin-multiplatform plugins, not for kotlin-android. Without it the
//    AbiBuildWorker fails with NoClassDefFoundError on JvmMetadataUtil. Resolve it explicitly
//    using the same KGP version that is on the compile classpath.
//
// This should be removed once the upstream KGP/AGP bug is fixed.
val kotlinMetadataForBcv: Configuration = configurations.create("kotlinMetadataForBcv") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add(
    kotlinMetadataForBcv.name,
    "org.jetbrains.kotlin:kotlin-metadata-jvm:${project.findVersion("kotlinGradlePlugin")}"
)

tasks.withType(KotlinApiBuildTask::class.java).configureEach {
    val compileTask = tasks.named("compileReleaseKotlin", KotlinCompile::class.java)
    inputClassesDirs.from(compileTask.flatMap { it.destinationDirectory })
    runtimeClasspath.from(kotlinMetadataForBcv)
    dependsOn(compileTask)
}

dokka {
    dokkaPublications.html {
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        if (name == "release") {
            perPackageOption {
                matchingRegex.set(".*internal.*")
                suppress.set(true)
                reportUndocumented.set(true) // Emit warnings about not documented members
            }
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl("https://github.com/embrace-io/embrace-android-sdk/blob/main/${project.name}/src/main/kotlin")
                remoteLineSuffix.set("#L")
            }
            externalDocumentationLinks.register("embrace-io-docs") {
                url("https://embrace.io/docs/android")
            }
        }
    }
    pluginsConfiguration.html {
        footerMessage.set("(c) embrace.io")
    }
}

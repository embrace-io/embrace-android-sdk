import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("embrace-prod-android-conventions")
    id("org.jetbrains.dokka")
}

kotlin.explicitApi()

// KGP doesn't finalize ABI validation for AGP 9 built-in Kotlin (KT-83410), so the reference dir
// and release class files are wired by hand. JvmTargetInfo is internal, hence the reflection.
@OptIn(ExperimentalAbiValidation::class)
kotlin.abiValidation {
    referenceDumpDir.set(layout.projectDirectory.dir("api"))
}

tasks.named("internalDumpKotlinAbi") {
    val compileTask = tasks.named("compileReleaseKotlin", KotlinCompile::class.java)
    val classes = files(compileTask.flatMap { it.destinationDirectory }).builtBy(compileTask)
    val info = Class.forName("org.jetbrains.kotlin.gradle.tasks.abi.KotlinAbiDumpTaskImpl\$JvmTargetInfo")
        .getConstructor(String::class.java, FileCollection::class.java)
        .newInstance("", classes)
    @Suppress("UNCHECKED_CAST")
    (property("jvm") as ListProperty<Any>).add(info)
}

tasks.named("check") {
    dependsOn("checkKotlinAbi")
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

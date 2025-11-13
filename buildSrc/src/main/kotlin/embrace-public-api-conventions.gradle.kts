import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("embrace-prod-android-conventions")
    id("binary-compatibility-validator")
    id("org.jetbrains.dokka")
}

kotlin.explicitApi()

dokka {
    dokkaPublications.html {
        failOnWarning.set(true)
    }
    dokkaSourceSets.main {
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
    pluginsConfiguration.html {
        footerMessage.set("(c) embrace.io")
    }
}

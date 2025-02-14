import java.time.Duration
import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    kotlin("android") apply false
    kotlin("jvm") apply false
    alias(libs.plugins.google.ksp) apply false
    id("com.android.library") apply false
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

group = "io.embrace"
version = project.version

nexusPublishing {
    repositories {
        sonatype {
            username = System.getenv("SONATYPE_USERNAME")
            password = System.getenv("SONATYPE_PASSWORD")
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
        }
    }
    transitionCheckOptions {
        maxRetries.set(60)
        delayBetween.set(Duration.ofSeconds(20))
    }
    connectTimeout.set(Duration.ofMinutes(15))
    clientTimeout.set(Duration.ofMinutes(15))
}

subprojects {
    if (project.name == "embrace-android-sdk" || project.name == "embrace-android-api") {
        apply(plugin = "org.jetbrains.dokka")
    }

    tasks.withType<DokkaTaskPartial>().configureEach {
        dokkaSourceSets.configureEach {
            perPackageOption {
                skipDeprecated.set(false)
                reportUndocumented.set(true) // Emit warnings about not documented members
                includeNonPublic.set(false)

                // Suppress files in the internal package
                matchingRegex.set(".*.internal.*?")
                suppress.set(true)
            }
            suppressObviousFunctions.set(true)
            noAndroidSdkLink.set(false)
        }
    }
}

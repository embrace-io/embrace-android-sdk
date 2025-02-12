import java.time.Duration
import org.jetbrains.dokka.gradle.DokkaTaskPartial

buildscript {
    repositories {
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }

    dependencies {
        classpath(libs.detekt.gradle.plugin)
        classpath(libs.dokka.gradle.plugin)
        classpath(libs.dokka.docs)
    }
}

plugins {
    id("com.google.devtools.ksp") version ("2.1.0-1.0.29") apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.dokka") version "1.9.20"
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

allprojects {
    repositories {
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }
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

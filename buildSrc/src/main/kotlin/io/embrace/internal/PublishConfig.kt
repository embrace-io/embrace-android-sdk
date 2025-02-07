package io.embrace.internal

import java.net.URI
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

fun Project.configurePublishing() {
    val publishing = project.extensions.getByType(PublishingExtension::class.java)

    // https://developer.android.com/studio/publish-library/upload-library
    publishing.apply {
        publications {
            register<MavenPublication>("release") {
                addPublication(this, "release")
            }
        }

        // configure repositories where the publication can be hosted
        repositories {
            // beta releases
            maven {
                credentials {
                    username = System.getenv("MAVEN_QA_USER")
                    password = System.getenv("MAVEN_QA_PASSWORD")
                }
                name = "Qa"
                url = URI.create("https://repo.embrace.io/repository/beta")
            }
            // the android-testing maven repository is used for publishing snapshots
            maven {
                credentials {
                    username = System.getenv("MAVEN_QA_USER")
                    password = System.getenv("MAVEN_QA_PASSWORD")
                }
                name = "Snapshot"
                url = URI.create("https://repo.embrace.io/repository/android-testing")
            }
        }
    }

    val signing = project.extensions.getByType(SigningExtension::class.java)

    signing.apply {
        val keyId = System.getenv("mavenSigningKeyId")
        val key = System.getenv("mavenSigningKeyRingFileEncoded")
        val password = System.getenv("mavenSigningKeyPassword")
        useInMemoryPgpKeys(keyId, key, password)
        sign(publishing.publications.getByName("release"))
    }

    project.tasks.withType(Sign::class).configureEach {
        enabled = !project.version.toString().endsWith("-SNAPSHOT")
    }
}

private fun Project.addPublication(publication: MavenPublication, componentName: String) = with(publication) {
    groupId = "io.embrace"
    artifactId = project.name
    version = project.version.toString()

    afterEvaluate {
        from(components[componentName])
    }

    // append some license metadata to the POM.
    pom {
        name.set(project.name)
        description.set("Embrace Android SDK")
        url.set("https://github.com/embrace-io/embrace-android-sdk")
        licenses {
            license {
                name.set("Embrace License")
                url.set("https://embrace.io/docs/terms-of-service/")
            }
        }
        developers {
            developer {
                id.set("dev1")
                name.set("Embrace")
                email.set("support@embrace.io")
            }
        }
        scm {
            connection.set("scm:git:github.com/embrace-io/embrace-android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/embrace-io/embrace-android-sdk.git")
            url.set("https://github.com/embrace-io/embrace-android-sdk/tree/main")
        }
    }
}

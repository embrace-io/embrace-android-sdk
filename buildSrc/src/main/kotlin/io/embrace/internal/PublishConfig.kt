package io.embrace.internal

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project

fun Project.configurePublishing() {
    if (disableDefaultPublishConfig()) {
        return
    }
    project.pluginManager.withPlugin("com.vanniktech.maven.publish") {
        val mavenPublishing = project.extensions.getByType(MavenPublishBaseExtension::class.java)
        mavenPublishing.apply {
            coordinates("io.embrace", project.name, project.version.toString())

            publishToMavenCentral()
            signAllPublications()

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
    }
}

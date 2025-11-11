plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    coordinates("io.embrace", project.name, project.version.toString())

    publishToMavenCentral()

    // Only enable signing if credentials are available (e.g., in CI during publish or locally)
    if (project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }
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

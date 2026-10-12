plugins {
    id("maven-publish")
}

check(path in integrationTestPublishedProjects) {
    "$path publishes artifacts, so it must be added to integrationTestPublishedProjects in buildSrc."
}

val integrationRepoDir = isolated.rootProject.projectDirectory.dir(INTEGRATION_TEST_REPO_PATH)

publishing {
    repositories {
        maven {
            name = "integrationTest"
            url = uri(integrationRepoDir)
        }
    }
}

// Gradle publishes SNAPSHOTs to repositories other than mavenLocal with a unique timestamped version. Delete
// previous copies before publishing so the repository only holds the latest artifacts. This keeps the input fingerprint
// stable for the integration tests.
tasks.withType<PublishToMavenRepository>().configureEach {
    val task = this
    val publicationDir = providers.provider {
        if (task.repository.name == "integrationTest") {
            val pub = task.publication
            integrationRepoDir.dir("${pub.groupId.replace('.', '/')}/${pub.artifactId}/${pub.version}").asFile
        } else {
            null
        }
    }
    doFirst {
        publicationDir.orNull?.deleteRecursively()
    }
}

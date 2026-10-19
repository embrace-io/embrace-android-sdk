import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Publish without javadoc/source jars unless publishing to Maven Central or `-PfullPublish` is set, as integration
 * tests never read them.
 */
fun MavenPublishBaseExtension.configureIntegrationPublish(project: Project) {
    val mavenCentralRequested = project.gradle.startParameter.taskNames.any { it.contains("MavenCentral") }
    if (mavenCentralRequested || project.providers.gradleProperty("fullPublish").isPresent) {
        return
    }
    configureBasedOnAppliedPlugins(javadocJar = JavadocJar.None(), sourcesJar = SourcesJar.None())

    // fail loudly if a Maven Central publish is reached indirectly
    project.gradle.taskGraph.whenReady {
        if (allTasks.any { it.project == project && it.name.contains("MavenCentral") }) {
            throw GradleException("Pass -PfullPublish when publishing to Maven Central")
        }
    }
}

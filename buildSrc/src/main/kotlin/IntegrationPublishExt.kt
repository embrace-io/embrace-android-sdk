import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Publish without javadoc/source jars when `-PintegrationPublish` is set, which speeds up execution time
 * as tests never read these
 */
fun MavenPublishBaseExtension.configureIntegrationPublish(project: Project) {
    if (!project.providers.gradleProperty("integrationPublish").isPresent) {
        return
    }
    configureBasedOnAppliedPlugins(javadocJar = JavadocJar.None(), sourcesJar = SourcesJar.None())

    // fail loudly if we're somehow attempting to publish...
    project.gradle.taskGraph.whenReady {
        if (allTasks.any { it.project == project && it.name.contains("MavenCentral") }) {
            throw GradleException("-PintegrationPublish must not be used when publishing to Maven Central")
        }
    }
}

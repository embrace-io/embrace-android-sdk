import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

fun Project.versionCatalog(name: String = "libs"): VersionCatalog {
    return extensions.getByType(VersionCatalogsExtension::class.java).named(name)
}

fun Project.findLibrary(alias: String): Any {
    val libs = versionCatalog()
    return libs.findLibrary(alias).get().get()
}

fun Project.findVersion(alias: String): String {
    val libs = versionCatalog()
    return libs.findVersion(alias).get().requiredVersion
}

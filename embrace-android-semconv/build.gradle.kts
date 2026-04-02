import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("embrace-prod-jvm-conventions")
}

// Exclude generated sources from Detekt
tasks.withType<Detekt>().configureEach {
    exclude("**/io/embrace/android/embracesdk/semconv/**")
}

abstract class GenerateEmbraceSemanticConventionsTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val semconvDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templatesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    var language: String = ""

    @TaskAction
    fun run() {
        try {
            val result = execOps.exec {
                commandLine(
                    "weaver", "registry", "generate",
                    "-r", semconvDir.get().asFile.absolutePath,
                    "--templates", templatesDir.get().asFile.absolutePath,
                    language,
                    outputDir.get().asFile.absolutePath,
                )
            }
            if (result.exitValue != 0) {
                throw GradleException("Weaver command failed with ${result.exitValue}")
            }
        } catch (exc: Exception) {
            throw GradleException(
                "Weaver command failed. Is weaver installed? https://github.com/open-telemetry/weaver/releases",
                exc,
            )
        }
    }
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventions") {
    semconvDir.set(layout.projectDirectory.dir("src/main/semconv"))
    templatesDir.set(layout.projectDirectory.dir("src/main/templates"))
    language = "kotlin"
    outputDir.set(layout.projectDirectory.dir("src/main/kotlin/io/embrace/android/embracesdk/semconv"))
}

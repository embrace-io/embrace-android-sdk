import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    id("embrace-jvm-conventions")
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

    var language: String = ""

    @TaskAction
    fun run() {
        try {
            execOps.exec {
                commandLine(
                    "weaver", "registry", "generate",
                    "-r", semconvDir.get().asFile.absolutePath,
                    "--templates", templatesDir.get().asFile.absolutePath,
                    language,
                    outputDir.get().asFile.absolutePath,
                )
                isIgnoreExitValue = true
            }
        } catch (exc: Exception) {
            throw GradleException(
                "Weaver command failed. Is weaver installed? Try: cargo install weaver-toolchain",
                exc,
            )
        }
    }
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventions") {
    group = "code generation"
    description = "Generates Kotlin semantic convention constants from Weaver YAML definitions"
    semconvDir.set(layout.projectDirectory.dir("semconv"))
    templatesDir.set(layout.projectDirectory.dir("templates"))
    language = "embrace"
    outputDir.set(layout.projectDirectory.dir("src/main/kotlin/io/embrace/android/embracesdk/semconv"))
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventionsGo") {
    group = "code generation"
    description = "Generates Go semantic convention constants from Weaver YAML definitions"
    semconvDir.set(layout.projectDirectory.dir("semconv"))
    templatesDir.set(layout.projectDirectory.dir("templates"))
    language = "go"
    outputDir.set(layout.projectDirectory.dir("generated/go"))
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventionsSwift") {
    group = "code generation"
    description = "Generates Swift semantic convention constants from Weaver YAML definitions"
    semconvDir.set(layout.projectDirectory.dir("semconv"))
    templatesDir.set(layout.projectDirectory.dir("templates"))
    language = "swift"
    outputDir.set(layout.projectDirectory.dir("generated/swift"))
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventionsDart") {
    group = "code generation"
    description = "Generates Dart semantic convention constants from Weaver YAML definitions"
    semconvDir.set(layout.projectDirectory.dir("semconv"))
    templatesDir.set(layout.projectDirectory.dir("templates"))
    language = "dart"
    outputDir.set(layout.projectDirectory.dir("generated/dart"))
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventionsJavaScript") {
    group = "code generation"
    description = "Generates JavaScript semantic convention constants from Weaver YAML definitions"
    semconvDir.set(layout.projectDirectory.dir("semconv"))
    templatesDir.set(layout.projectDirectory.dir("templates"))
    language = "javascript"
    outputDir.set(layout.projectDirectory.dir("generated/javascript"))
}

tasks.register<GenerateEmbraceSemanticConventionsTask>("generateEmbraceSemanticConventionsCSharp") {
    group = "code generation"
    description = "Generates C# semantic convention constants from Weaver YAML definitions"
    semconvDir.set(layout.projectDirectory.dir("semconv"))
    templatesDir.set(layout.projectDirectory.dir("templates"))
    language = "csharp"
    outputDir.set(layout.projectDirectory.dir("generated/csharp"))
}

tasks.register("generateAllEmbraceSemanticConventions") {
    group = "code generation"
    description = "Generates semantic convention constants for all supported languages"
    dependsOn(
        "generateEmbraceSemanticConventions",
        "generateEmbraceSemanticConventionsGo",
        "generateEmbraceSemanticConventionsSwift",
        "generateEmbraceSemanticConventionsDart",
        "generateEmbraceSemanticConventionsJavaScript",
        "generateEmbraceSemanticConventionsCSharp",
    )
}

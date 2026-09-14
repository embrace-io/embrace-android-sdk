package io.embrace.analysis.campaign

import io.embrace.analysis.common.proc.Processes
import java.nio.file.Files
import java.nio.file.Path

/**
 * The app resolves the SDK coordinate the cell claims to measure. Read back from Gradle's dependency
 * tree, never from the catalog text: the catalog says what was asked for, the tree says what was got.
 * `local` means the repo's own `gradle.properties` version.
 */
class SdkMatch(
    private val repo: Path,
    private val gradle: (List<String>, Path) -> Processes.Output,
    private val expected: String,
    private val log: (String) -> Unit,
) : Invariant {

    private val example: Path = repo.resolve("examples/ExampleApp")

    override fun check(): Check {
        val resolved = resolvedSdkVersion()
            ?: return Check(NAME, false, "could not read a resolved embrace-android-sdk coordinate")
        log("resolved SDK: $resolved")
        if (expected == "local") {
            val local = Files.readAllLines(repo.resolve("gradle.properties")).firstOrNull { it.startsWith("version=") }
                ?.substringAfter("=")?.trim()
            return Check(NAME, local != null && local in resolved, "expected local ${local ?: "None"}, resolved $resolved")
        }
        return Check(NAME, expected in resolved, "expected $expected, resolved $resolved")
    }

    /** The SDK coordinate the app actually resolves - never the catalog text. */
    fun resolvedSdkVersion(): String? {
        val gradlew = example.resolve("gradlew").toString()
        val out = gradle(
            listOf(gradlew, "-p", example.toString(), ":app:dependencies", "--configuration", "benchmarkRuntimeClasspath", "-q"),
            example,
        )
        return resolvedSdkLine(out.stdout)
    }

    companion object {
        const val NAME: String = "sdk matches"

        /** The `io.embrace:embrace-android-sdk` line of a Gradle dependency tree, tree glyphs stripped. */
        fun resolvedSdkLine(stdout: String): String? =
            stdout.lines().firstOrNull {
                "io.embrace:embrace-android-sdk" in it
            }?.trim()?.trimStart('+', '\\', '-', '|', ' ')?.trim()
    }
}

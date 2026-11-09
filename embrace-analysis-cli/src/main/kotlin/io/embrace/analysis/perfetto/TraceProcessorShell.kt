package io.embrace.analysis.perfetto

import java.io.PrintStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Perfetto's native `trace_processor_shell` at the [version] the version catalog pins (`perfettoTraceProcessor` in
 * gradle/libs.versions.toml), extracted on first use into [cacheDir] from the platform's archive in Perfetto's GitHub
 * release, which [releasesUrl] serves. Downloads are announced on [log].
 */
class TraceProcessorShell(
    private val log: PrintStream,
    private val version: String = pinnedVersion(),
    private val cacheDir: Path = Paths.get(System.getProperty("user.home"), ".cache", "embrace-startup-tools", "trace-processor"),
    private val releasesUrl: String = RELEASES_URL,
) {

    // Perfetto names a release by its version with a `v` in front, in its tags and its release downloads' paths
    private val release: String = "v$version"

    /**
     * The path of the binary for [platform], downloading it when the cache has no copy. Throws [IllegalStateException]
     * when the release's archive for the platform has no trace_processor_shell.
     */
    fun install(platform: Platform = Platform.current()): Path {
        val binary = cacheDir.resolve(release).resolve(platform.key).resolve(FILE_NAME)
        if (Files.isRegularFile(binary)) {
            return binary
        }
        log.println("downloading trace_processor_shell $release for ${platform.key}")
        Files.createDirectories(binary.parent)
        val partial = Files.createTempFile(binary.parent, FILE_NAME, ".partial")
        try {
            // the archive holds Perfetto's host tools under a directory named for the platform
            val entry = "${platform.key}/$FILE_NAME"
            URI.create("$releasesUrl/$release/${platform.key}.zip").toURL().openStream().use { download ->
                ZipInputStream(download).use { archive ->
                    checkNotNull(generateSequence { archive.nextEntry }.firstOrNull { it.name == entry }) {
                        "the $release archive for ${platform.key} has no $entry"
                    }
                    Files.copy(archive, partial, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            check(partial.toFile().setExecutable(true)) { "can't make $partial executable" }
            Files.move(partial, binary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(partial)
        }
        return binary
    }

    /** The platforms the tools run on, macOS on Apple silicon or Intel, by the [key] Perfetto publishes them under. */
    enum class Platform(val key: String) {
        MAC_ARM64("mac-arm64"),
        MAC_AMD64("mac-amd64"),
        ;

        companion object {

            /**
             * The platform this JVM runs on, from the `os.name` and `os.arch` system properties [osName] and [osArch].
             * Throws [IllegalStateException] for anything but macOS.
             */
            fun current(
                osName: String = System.getProperty("os.name"),
                osArch: String = System.getProperty("os.arch"),
            ): Platform {
                val mac = osName.lowercase().startsWith("mac")
                return when {
                    mac && osArch in setOf("aarch64", "arm64") -> MAC_ARM64
                    mac && osArch in setOf("x86_64", "amd64") -> MAC_AMD64
                    else -> error("the startup tools run on macOS, not $osName on $osArch")
                }
            }
        }
    }

    companion object {

        /** The version the version catalog pins, as the build wrote it into the tools' resources. */
        fun pinnedVersion(): String =
            checkNotNull(TraceProcessorShell::class.java.getResource(VERSION_RESOURCE)) { "missing $VERSION_RESOURCE" }
                .readText()
                .trim()

        private const val VERSION_RESOURCE = "trace_processor_shell.version"
        private const val RELEASES_URL = "https://github.com/google/perfetto/releases/download"
        private const val FILE_NAME = "trace_processor_shell"
    }
}

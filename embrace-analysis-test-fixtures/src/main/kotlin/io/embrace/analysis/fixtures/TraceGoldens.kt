package io.embrace.analysis.fixtures

import io.embrace.analysis.common.io.Zips
import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * The frozen trace-layer goldens: for each fixture trace, the raw `trace_processor -q` stdout
 * of every query (`<query>.csv`) and the frozen parse of it (`python_parsed.json`). Produced by
 * the `_producers/` scripts inside the set; see the trace-goldens MANIFEST for provenance. They are
 * generated once and never edited, so they are stored as a single archive and unpacked on demand. The
 * trace binaries themselves are NOT in the repo, so tests that need them look under the repo's scratch
 * output directory and skip when absent.
 */
object TraceGoldens {

    val QUERY_NAMES: List<String> = listOf(
        "startup_metrics", "init_window_sched", "variance_metrics", "outlier_metrics", "foreign_gc_overlap",
        "health", "window_emb_sdk_start", "window_composed", "signals",
    )

    data class TraceGolden(val device: String, val stem: String, val dir: Path) {
        fun csv(query: String): String = Files.readString(dir.resolve("$query.csv"))
        fun parsed(): JsonObject = StartupJson.parseToJsonElement(Files.readString(dir.resolve("python_parsed.json"))).jsonObject
        val traceFileName: String get() = "$stem.perfetto-trace"
    }

    /**
     * The goldens, unpacked. `trace-goldens/data.zip` is unpacked once per test JVM into a temporary
     * directory; a `trace-goldens/` left unpacked by a producer script is used as it stands.
     */
    fun root(): Path = unpacked

    fun all(): List<TraceGolden> {
        val root = root()
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { devices ->
            devices.filter {
                Files.isDirectory(it) && !it.fileName.toString().startsWith("_")
            }.sorted().toList().flatMap { deviceDir ->
                Files.list(deviceDir).use { stems ->
                    stems.filter { Files.isDirectory(it) }.sorted().toList()
                        .map { TraceGolden(deviceDir.fileName.toString(), it.fileName.toString(), it) }
                }
            }
        }
    }

    /** Frozen stdout of the golden CLI run over one device's traces, or null when not captured. */
    fun cliStdout(name: String): String? =
        root().resolve("_cli").resolve(name).takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) }

    /**
     * The captured trace for a golden, if this machine has the durable fixture set: `<root>/<device>/pass1/<trace>`
     * where the root is `STARTUP_TOOLS_TRACE_FIXTURES` when set, else `claude-output/trace-fixtures` under the
     * repo. The traces are not in the repo (345 MB); the live gate skips when they are absent.
     */
    fun traceFile(golden: TraceGolden, repoRoot: Path): Path? {
        val root = System.getenv("STARTUP_TOOLS_TRACE_FIXTURES")?.let { Path.of(it) }
            ?: repoRoot.resolve("claude-output/trace-fixtures")
        val candidate = root.resolve(golden.device).resolve("pass1").resolve(golden.traceFileName)
        return candidate.takeIf { Files.isRegularFile(it) }
    }

    private val unpacked: Path by lazy {
        val dir = Fixtures.path("trace-goldens").toPath()
        val archive = dir.resolve("data.zip")
        if (Files.isRegularFile(archive)) Zips.unpackToTemp(archive) else dir
    }

    /** The repo root, from the module's working directory (Gradle runs tests in the module dir). */
    fun repoRoot(): Path {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            if (Files.isDirectory(dir.resolve(".git")) || Files.isRegularFile(dir.resolve(".git"))) return dir
            dir = dir.parent
        }
        error("not inside a git checkout")
    }
}

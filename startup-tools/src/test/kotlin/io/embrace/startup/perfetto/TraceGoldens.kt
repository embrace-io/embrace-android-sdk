package io.embrace.startup.perfetto

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * The frozen Python trace-layer outputs: for each fixture trace, the raw `trace_processor -q` stdout
 * of every query (`<query>.csv`) and the Python's own parse of it (`python_parsed.json`). Produced by
 * `claude-output/2026-08-26-kotlin-port/goldens/dump_trace_goldens.py`; the trace binaries themselves
 * are NOT in the repo, so tests that need them look under the repo's `claude-output/` and skip when
 * absent.
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

    fun root(): Path = SchemaRoundTripTest.fixturesRoot().toPath().resolve("trace-goldens")

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

    /** Frozen stdout of a Python CLI run over one device's traces, or null when not captured. */
    fun cliStdout(name: String): String? =
        root().resolve("_cli").resolve(name).takeIf { Files.isRegularFile(it) }?.let { Files.readString(it) }

    /** The captured trace for a golden, if this machine has the durable fixture set. */
    fun traceFile(golden: TraceGolden, repoRoot: Path): Path? {
        val candidate = repoRoot.resolve("claude-output/2026-08-26-kotlin-port/fixtures/traces")
            .resolve(golden.device).resolve("pass1").resolve(golden.traceFileName)
        return candidate.takeIf { Files.isRegularFile(it) }
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

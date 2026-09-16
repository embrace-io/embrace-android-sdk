package io.embrace.android.embracesdk.internal.perfetto

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** What androidx.benchmark calls the manifest it rewrites on every run. */
private const val MANIFEST_SUFFIX = "-benchmarkData.json"

/** The profiler whose outputs are traces. */
private const val TRACE_PROFILER = "PerfettoTrace"

private val JSON = Json { ignoreUnknownKeys = true }

/**
 * The traces of the run collected into [dir], in benchmark then iteration order.
 *
 * `grab-macrobenchmark-output.sh` copies into its destination without clearing it, so a directory
 * reused across runs holds traces of several. androidx.benchmark's manifest is rewritten every run and
 * names exactly the traces that run produced, so it decides, and stale traces beside them are ignored.
 *
 * Throws [IOException] when there is no manifest, it cannot be read, or it names a trace that is not
 * there.
 */
internal fun discoverIterations(dir: File): List<IterationTrace> {
    val manifest = dir.listFiles { file: File -> file.isFile && file.name.endsWith(MANIFEST_SUFFIX) }
        ?.maxByOrNull(File::lastModified)
        ?: throw IOException("no *$MANIFEST_SUFFIX in ${dir.path}, so the run's traces cannot be told apart")
    return manifestTraces(dir, manifest)
}

private fun manifestTraces(dir: File, manifest: File): List<IterationTrace> {
    val data = try {
        JSON.decodeFromString<BenchmarkData>(manifest.readText())
    } catch (exc: SerializationException) {
        throw IOException("could not read ${manifest.path} as benchmark data: ${exc.message}", exc)
    }
    return data.benchmarks.flatMap { benchmark ->
        benchmark.profilerOutputs
            .filter { it.type == TRACE_PROFILER }
            .mapIndexed { index, output ->
                IterationTrace(benchmark.label, index, File(dir, output.filename))
            }
    }.onEach { iteration ->
        if (!iteration.file.isFile) {
            throw IOException("${manifest.path} names a trace that is not there: ${iteration.file.path}")
        }
    }
}

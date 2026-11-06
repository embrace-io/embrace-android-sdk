package io.embrace.android.embracesdk.internal.perfetto.iterations

import io.embrace.android.embracesdk.internal.perfetto.resolvedMetadata
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSource
import okio.FileSystem
import okio.Path
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
internal fun discoverIterations(fileSystem: FileSystem, dir: Path): List<IterationTrace> {
    val manifest = fileSystem.listOrNull(dir)
        ?.mapNotNull { path -> fileSystem.resolvedMetadata(path)?.let { path to it } }
        ?.filter { (path, metadata) -> metadata.isRegularFile && path.name.endsWith(MANIFEST_SUFFIX) }
        ?.maxByOrNull { (_, metadata) -> metadata.lastModifiedAtMillis ?: 0L }
        ?.first
        ?: throw IOException("no *$MANIFEST_SUFFIX in $dir, so the run's traces cannot be told apart")
    return manifestTraces(fileSystem, dir, manifest)
}

private fun manifestTraces(fileSystem: FileSystem, dir: Path, manifest: Path): List<IterationTrace> {
    val data = try {
        JSON.decodeFromString<BenchmarkData>(fileSystem.read(manifest, BufferedSource::readUtf8))
    } catch (exc: SerializationException) {
        throw IOException("could not read $manifest as benchmark data: ${exc.message}", exc)
    }
    return data.benchmarks.flatMap { benchmark ->
        benchmark.profilerOutputs
            .filter { it.type == TRACE_PROFILER }
            .mapIndexed { index, output ->
                IterationTrace(benchmark.label, index, dir / output.filename)
            }
    }.onEach { iteration ->
        if (fileSystem.resolvedMetadata(iteration.file)?.isRegularFile != true) {
            throw IOException("$manifest names a trace that is not there: ${iteration.file}")
        }
    }
}

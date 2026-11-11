package io.embrace.analysis.benchmark

import java.nio.file.Files
import java.nio.file.Path

/** Makes [IterationTrace]s from macrobenchmark trace files, named `..._iterNNN_<timestamp>.perfetto-trace`. */
class IterationTraceFactory {

    /** The trace at [path]; a name without an `iterNNN` has no index and keeps its whole name as its short name. */
    fun create(path: Path): IterationTrace {
        val name = path.fileName.toString()
        return IterationTrace(
            path = path,
            index = ITER_INDEX.find(name)?.groupValues?.get(1)?.toInt(),
            shortName = ITER_TAIL.find(name)?.value?.removeSuffix(EXTENSION) ?: name,
        )
    }

    /** The traces directly in [dir], in iteration order; a trace without an index comes after every numbered one. */
    fun listIn(dir: Path): List<IterationTrace> =
        Files.newDirectoryStream(dir, "*$EXTENSION").use { files -> files.map(::create) }
            .sortedWith(compareBy(nullsLast()) { trace: IterationTrace -> trace.index }.thenBy { it.path.fileName })

    companion object {
        const val EXTENSION = ".perfetto-trace"
        private val ITER_INDEX = Regex("iter(\\d+)")
        private val ITER_TAIL = Regex("iter\\d+.*")
    }
}

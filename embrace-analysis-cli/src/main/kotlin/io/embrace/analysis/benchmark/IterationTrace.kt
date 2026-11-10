package io.embrace.analysis.benchmark

import java.nio.file.Path

/**
 * One iteration's macrobenchmark trace file: its [path], the `NNN` of `iterNNN` in its name (null when the name has
 * none), and its [shortName], the name from `iterNNN` to before the extension.
 */
data class IterationTrace(
    val path: Path,
    val index: Int?,
    val shortName: String,
)

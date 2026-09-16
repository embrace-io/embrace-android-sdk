package io.embrace.android.embracesdk.internal.perfetto

import java.io.File

/**
 * One iteration's trace within a macrobenchmark run.
 *
 * @param benchmark the benchmark that produced it, as `<class>.<method>`.
 * @param index which iteration of that benchmark this is, counting from zero.
 * @param file the trace itself.
 */
internal data class IterationTrace(
    val benchmark: String,
    val index: Int,
    val file: File,
)

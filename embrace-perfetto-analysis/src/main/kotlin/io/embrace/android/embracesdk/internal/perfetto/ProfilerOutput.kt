package io.embrace.android.embracesdk.internal.perfetto

import kotlinx.serialization.Serializable

/**
 * One file a profiler wrote, named relative to the directory the run was collected into.
 *
 * @param type which profiler wrote it. Only `PerfettoTrace` is of interest here.
 * @param label how androidx.benchmark describes it, e.g. `Trace Iteration 0`.
 * @param filename the file, with no directory part.
 */
@Serializable
internal data class ProfilerOutput(
    val type: String = "",
    val label: String = "",
    val filename: String = "",
)

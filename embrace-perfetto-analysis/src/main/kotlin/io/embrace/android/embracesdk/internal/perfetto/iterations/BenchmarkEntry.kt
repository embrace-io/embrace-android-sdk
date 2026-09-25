package io.embrace.android.embracesdk.internal.perfetto.iterations

import kotlinx.serialization.Serializable

/**
 * One benchmark method of a run, and the files its profilers left behind.
 *
 * @param name the test method.
 * @param className the benchmark class, fully qualified.
 * @param profilerOutputs what its profilers wrote, one entry per iteration for a trace profiler.
 */
@Serializable
internal data class BenchmarkEntry(
    val name: String = "",
    val className: String = "",
    val profilerOutputs: List<ProfilerOutput> = emptyList(),
) {
    val label: String get() = "${className.substringAfterLast('.')}.$name"
}

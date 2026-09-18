package io.embrace.android.embracesdk.internal.perfetto.iterations

import kotlinx.serialization.Serializable

/**
 * The `<package>-benchmarkData.json` androidx.benchmark writes beside the traces of a run.
 */
@Serializable
internal data class BenchmarkData(val benchmarks: List<BenchmarkEntry> = emptyList())

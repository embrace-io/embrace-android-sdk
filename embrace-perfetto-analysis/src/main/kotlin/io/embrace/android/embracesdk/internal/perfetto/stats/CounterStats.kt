package io.embrace.android.embracesdk.internal.perfetto.stats

import kotlinx.serialization.Serializable

/**
 * What one counter recorded across a whole trace.
 *
 * @param name the counter, as atrace recorded it.
 * @param tids the threads that published it, ascending. Usually one.
 * @param sampleCount how many values the trace recorded.
 * @param firstValue the earliest value.
 * @param lastValue the latest value.
 * @param maxValue the largest value.
 * @param total everything the counter counted, restarts included. See [calculateCounters].
 * @param readings every value, in the order the trace recorded them.
 */
@Serializable
internal data class CounterStats(
    val name: String,
    val tids: List<Int>,
    val sampleCount: Int,
    val firstValue: Long,
    val lastValue: Long,
    val maxValue: Long,
    val total: Long,
    val readings: List<CounterReading>,
)

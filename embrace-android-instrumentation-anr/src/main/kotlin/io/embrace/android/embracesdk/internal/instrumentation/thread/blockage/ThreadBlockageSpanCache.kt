package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.payload.Span
import kotlin.random.Random

/**
 * Maps thread blockage intervals to spans, reusing the previous mapping where possible.
 *
 * Completed intervals are immutable, so a [Span] is reused for as long as the sampler returns the
 * same interval instance. Clearing an interval's samples replaces the instance, so it gets remapped.
 */
internal class ThreadBlockageSpanCache(
    private val clock: Clock,
    private val random: Random,
) {

    private val lock = Any()
    private var cache: Map<Long, Pair<ThreadBlockageInterval, Span>> = emptyMap()

    fun map(intervals: List<ThreadBlockageInterval>): List<Span> = synchronized(lock) {
        val next = HashMap<Long, Pair<ThreadBlockageInterval, Span>>(intervals.size)
        val spans = intervals.map { interval ->
            val key = interval.startTime
            if (next.containsKey(key)) {
                return@map mapIntervalToSpan(interval, clock, generateId(16, random), generateId(8, random))
            }
            val prev = cache[key]
            val span = when {
                prev == null -> mapIntervalToSpan(interval, clock, generateId(16, random), generateId(8, random))
                prev.first === interval -> prev.second
                else -> mapIntervalToSpan(
                    interval,
                    clock,
                    prev.second.traceId ?: generateId(16, random),
                    prev.second.spanId ?: generateId(8, random),
                )
            }
            next[key] = Pair(interval, span)
            span
        }
        cache = next
        spans
    }

    fun clear() {
        synchronized(lock) {
            cache = emptyMap()
        }
    }
}

package io.embrace.android.embracesdk.internal.utils

import java.util.concurrent.atomic.AtomicLong

/**
 * A counter published to system traces under [name], prefixed by "emb-" like a trace section.
 */
class TraceCounter(private val name: String) {

    private val total = AtomicLong()

    /**
     * Adds [delta] to the running total and publishes it.
     */
    fun add(delta: Long) {
        SystemTrace.counter(name, total.addAndGet(delta))
    }
}

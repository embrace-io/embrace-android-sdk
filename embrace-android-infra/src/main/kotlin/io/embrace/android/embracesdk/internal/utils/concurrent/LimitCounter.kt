package io.embrace.android.embracesdk.internal.utils.concurrent

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe integer based limit counter.
 */
@JvmInline
value class LimitCounter private constructor(private val combined: AtomicLong) {

    constructor(capacity: Int) : this(AtomicLong(combine(capacity, 0)))

    val capacity: Int get() = combined.get().capacity

    /**
     * The estimated number of times [increment] has returned `true` since construction (or the last [reset]). The value is only an
     * estimate because the value may change with concurrent [increment] and [reset] calls.
     */
    val count: Int get() = combined.get().count

    val remaining: Int
        get() {
            val v = combined.get()
            return v.capacity - v.count
        }

    fun increment(): Boolean {
        // weak + clamped incrementAndGet
        while (true) {
            val current = combined.get()
            val capacity = current.capacity
            val count = current.count

            if (count >= capacity) {
                return false
            } else {
                val newValue = combine(capacity, count + 1)
                @Suppress("DEPRECATION")
                if (combined.weakCompareAndSet(current, newValue)) {
                    return true
                }
            }
        }
    }

    fun snapshot(): Snapshot {
        val value = combined.get()
        return Snapshot(
            capacity = value.capacity,
            count = value.count,
        )
    }

    /**
     * Reset this Limit ([count] will be `0`).
     */
    fun reset() {
        combined.set(combine(this@LimitCounter.capacity, 0))
    }

    private val Long.count: Int
        get() = (this and MASK32).toInt()
    private val Long.capacity: Int
        get() = ((this ushr 32) and MASK32).toInt()

    data class Snapshot(val capacity: Int, val count: Int)

    internal companion object {
        const val MASK32 = 0xffffffffL

        fun combine(capacity: Int, count: Int): Long {
            return (capacity.toLong() shl 32) or count.toLong()
        }
    }
}

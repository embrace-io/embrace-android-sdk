package io.embrace.android.embracesdk.internal.utils.concurrent

/**
 * Stops a block from running again on a thread that is already inside it, e.g. customer code that triggers the code calling it.
 */
@JvmInline
value class ReentrancyGuard private constructor(@PublishedApi internal val entered: ThreadLocal<Unit>) {
    constructor() : this(ThreadLocal())

    /**
     * Runs [block], or returns the result of [onReentry] if this thread is already inside a [block] of this guard.
     */
    inline fun <T> runExclusive(onReentry: () -> T, block: () -> T): T {
        if (entered.get() != null) {
            return onReentry()
        }
        entered.set(Unit)
        try {
            return block()
        } finally {
            entered.remove()
        }
    }
}
